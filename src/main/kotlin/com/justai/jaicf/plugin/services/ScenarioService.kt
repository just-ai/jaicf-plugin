package com.justai.jaicf.plugin.services

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.justai.jaicf.plugin.APPEND_CONTEXT_ARGUMENT_NAME
import com.justai.jaicf.plugin.APPEND_METHOD_NAME
import com.justai.jaicf.plugin.APPEND_OTHER_ARGUMENT_NAME
import com.justai.jaicf.plugin.Append
import com.justai.jaicf.plugin.CREATE_MODEL_METHOD_NAME
import com.justai.jaicf.plugin.DO_APPEND_METHOD_NAME
import com.justai.jaicf.plugin.FALLBACK_METHOD_NAME
import com.justai.jaicf.plugin.SCENARIO_EXTENSIONS_CLASS_NAME
import com.justai.jaicf.plugin.SCENARIO_METHOD_NAME
import com.justai.jaicf.plugin.SCENARIO_MODEL_FIELD_NAME
import com.justai.jaicf.plugin.SCENARIO_PACKAGE
import com.justai.jaicf.plugin.STATE_BODY_ANNOTATION_NAME
import com.justai.jaicf.plugin.STATE_BODY_ARGUMENT_NAME
import com.justai.jaicf.plugin.STATE_DECLARATION_ANNOTATION_NAME
import com.justai.jaicf.plugin.STATE_METHOD_NAME
import com.justai.jaicf.plugin.STATE_NAME_ANNOTATION_ARGUMENT_NAME
import com.justai.jaicf.plugin.STATE_NAME_ANNOTATION_NAME
import com.justai.jaicf.plugin.STATE_NAME_ARGUMENT_NAME
import com.justai.jaicf.plugin.SafeValue
import com.justai.jaicf.plugin.Scenario
import com.justai.jaicf.plugin.Scenario.Condition.ACTUAL
import com.justai.jaicf.plugin.Scenario.Condition.CORRUPTED
import com.justai.jaicf.plugin.Scenario.Condition.MODIFIED
import com.justai.jaicf.plugin.Scenario.Condition.REMOVED
import com.justai.jaicf.plugin.ScenarioPackageFqName
import com.justai.jaicf.plugin.State
import com.justai.jaicf.plugin.StateIdentifier
import com.justai.jaicf.plugin.StateIdentifier.ExpressionIdentifier
import com.justai.jaicf.plugin.StateIdentifier.NoIdentifier
import com.justai.jaicf.plugin.StateIdentifier.PredefinedIdentifier
import com.justai.jaicf.plugin.actualCondition
import com.justai.jaicf.plugin.argumentExpression
import com.justai.jaicf.plugin.argumentExpressionsByAnnotation
import com.justai.jaicf.plugin.contains
import com.justai.jaicf.plugin.declaration
import com.justai.jaicf.plugin.findChildOfType
import com.justai.jaicf.plugin.findClass
import com.justai.jaicf.plugin.getFramingState
import com.justai.jaicf.plugin.getMethodAnnotations
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.isOverride
import com.justai.jaicf.plugin.isRemoved
import com.justai.jaicf.plugin.resolve
import com.justai.jaicf.plugin.rootBuilderClassFqName
import com.justai.jaicf.plugin.scenarioGraphBuilderClassFqName
import com.justai.jaicf.plugin.stringValueOrNull
import com.justai.jaicf.plugin.valueArgument
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

class ScenarioService(project: Project) {

    private val scenariosByFiles: SafeValue<Map<KtFile, List<Scenario>>> = SafeValue(emptyMap())
    private val modifiedFiles: MutableSet<KtFile> = mutableSetOf()
    private val builder: ScenarioBuilder = ScenarioBuilder(project)

    init {
        scenariosByFiles.tryToUpdate { builder.buildScenarios(project.projectScope()) }
    }

    fun getState(element: PsiElement) =
        getScenarios(element.containingFile)
            .filter { contains(it.innerState, element) }
            .firstNotNullResult { recursiveFindState(it.innerState, element) }

    fun resolveScenario(expr: KtReferenceExpression) =
        builder.getScenarioBody(expr)?.let { block ->
            getScenarios(block.containingFile).firstOrNull { contains(it.innerState, block) }
        }

    fun getScenarios(file: PsiFile): List<Scenario> {
        if (file !is KtFile)
            return emptyList()

        updateScenariosIfNeeded(file)

        return scenariosByFiles.value[file.originalFile]
            ?.filter { it.actualCondition == ACTUAL }
            ?: return emptyList()
    }

    fun getRootScenarios(): List<Scenario> {
        scenariosByFiles.value.keys.forEach { updateScenariosIfNeeded(it) }

        val notRootScenarios =
            scenariosByFiles.value.values.flatten().flatMap { it.allAppends() }.mapNotNull { it.resolve() }

        return (scenariosByFiles.value.values.flatten() - notRootScenarios)
            .filter { it.actualCondition == ACTUAL }
    }

    fun markFileAsModified(file: KtFile) {
        invalidScenariosIfModified(file)
        modifiedFiles += file
    }

    fun getScenarioReference(element: PsiElement) = builder.getScenarioReference(element)

    private fun invalidScenariosIfModified(file: KtFile) {
        scenariosByFiles.value[file]?.forEach { scenario ->
            when {
                scenario.innerState.callExpression.isRemoved -> {
                    scenario.condition = REMOVED
                }

                scenario.innerState.textHashCode != scenario.innerState.callExpression.text.hashCode() -> {
                    scenario.modified()
                }
            }
        }
    }

    private fun updateScenariosIfNeeded(file: KtFile) {
        if (
            scenariosByFiles.value[file]?.all { it.condition == ACTUAL } == true &&
            file !in modifiedFiles
        )
            return

        scenariosByFiles.tryToUpdate { scenariosMap ->
            val scenarios = scenariosMap[file] ?: emptyList()
            scenarios
                .filter { it.condition == MODIFIED || it.condition == CORRUPTED }
                .onEach { builder.rebuildScenario(it) }
                .filter { it.actualCondition == ACTUAL }

            val newScenarios = tryToFindNewScenarios(file, scenarios)
            val containsRemoved =
                scenarios.any { it.condition == REMOVED || !it.innerState.callExpression.isExist }

            modifiedFiles.remove(file)
            return@tryToUpdate if (newScenarios.isEmpty() && !containsRemoved) {
                scenariosMap
            } else {
                val newScenarioList = (scenarios + newScenarios).filter { it.actualCondition == ACTUAL }

                scenariosMap.toMutableMap().also {
                    it[file] = newScenarioList
                }
            }
        }
    }

    private fun tryToFindNewScenarios(file: KtFile, existedScenarios: List<Scenario>) =
        builder.getScenariosCallExpressions(file.fileScope())
            .filter { existedScenarios.none { scenario -> scenario.innerState.callExpression == it } }
            .map { builder.buildScenario(it) }
            .filter { it.innerState.callExpression.isExist }

    private fun recursiveFindState(state: State, element: PsiElement): State =
        state.states.firstOrNull { contains(it, element) }?.let { recursiveFindState(it, element) } ?: state
}

private class ScenarioBuilder(val project: Project) {

    private val scenariosBuildMethods: List<PsiMethod> by lazy { getScenariosBuildMethodsDeclarations() }

    fun buildScenarios(scope: GlobalSearchScope) = getScenariosCallExpressions(scope)
        .map { buildScenario(it) }
        .groupBy(Scenario::file)

    fun getScenariosCallExpressions(scope: GlobalSearchScope) = scenariosBuildMethods
        .flatMap { ReferencesSearch.search(it, scope).findAll() }
        .map { it.element.parent }
        .filterIsInstance<KtCallExpression>()

    fun getScenarioBody(expr: KtReferenceExpression): KtExpression? {
        if (expr.isRemoved)
            return null

        return when (val resolvedElement = expr.resolve()) {
            is KtObjectDeclaration ->
                resolvedElement.declarations
                    .filter { it.name == SCENARIO_MODEL_FIELD_NAME && it is KtProperty }
                    .mapNotNull { (it as KtProperty).initializer }
                    .firstOrNull()

            is KtProperty -> resolvedElement.initializer

            else -> {
                if (expr is KtCallExpression) {
                    val constructor = expr.findChildOfType<KtReferenceExpression>()?.resolve() ?: return null
                    val ktClass = constructor.getParentOfType<KtClass>(true) ?: return null
                    val body = ktClass.findChildOfType<KtClassBody>() ?: return null

                    body.declarations
                        .filter { it.name == SCENARIO_MODEL_FIELD_NAME && it is KtProperty }
                        .mapNotNull { (it as KtProperty).initializer }
                        .firstOrNull()
                } else {
                    null
                }
            }
        }
    }

    fun getScenarioReference(element: PsiElement): KtExpression? {
        val scenario = element.getFramingState()?.scenario ?: return null
        val scenarioDeclaration = scenario.declarationElement as? KtCallExpression ?: return null
        val property = scenarioDeclaration.parent as? KtProperty ?: return null
        return when {
            scenarioDeclaration.isScenario -> property
            scenarioDeclaration.isCreateModelFun -> property.getParentOfType<KtClassOrObject>(false)
            else -> null
        }
    }

    private fun getScenariosBuildMethodsDeclarations(): List<PsiMethod> {
        check(!DumbService.getInstance(project).isDumb) { "IDEA in dumb mode" }

        return findClass(SCENARIO_PACKAGE, SCENARIO_EXTENSIONS_CLASS_NAME, project)?.allMethods
            ?.filter { it.name == SCENARIO_METHOD_NAME || it.name == CREATE_MODEL_METHOD_NAME } ?: emptyList()
    }

    fun buildScenario(body: KtCallExpression) =
        Scenario(body.containingKtFile).apply {
            innerState = StateBuilder.buildState(body, null, this)
        }

    fun rebuildScenario(scenario: Scenario) {
        scenario.innerState = StateBuilder.buildState(scenario.innerState.callExpression, null, scenario)
        scenario.condition = ACTUAL
    }
}

private object StateBuilder {

    fun buildState(
        stateExpression: KtCallExpression,
        parentState: State? = null,
        scenario: Scenario,
    ): State {
        val identifier = stateExpression.identifierOfStateExpression()
        val statements = extractStatements(stateExpression)

        return State(identifier, parentState, scenario, stateExpression)
            .apply {
                appends = buildAppends(this, statements)
                states = buildStates(this, statements, scenario)
                textHashCode = stateExpression.text.hashCode()
            }
    }

    private fun extractStatements(stateExpression: KtCallExpression): List<KtCallExpression> {
        val bodyArgument =
            getAnnotatedLambdaArgument(stateExpression)
                ?: getAnnotatedLambdaBlockInDeclaration(stateExpression)
                ?: getLambdaArgumentByArgumentName(stateExpression)
                ?: return emptyList()

        return bodyArgument.bodyExpression?.statements?.mapNotNull { it as? KtCallExpression } ?: emptyList()
    }

    private fun getAnnotatedLambdaArgument(stateExpression: KtCallExpression) =
        (stateExpression.argumentExpressionsByAnnotation(STATE_BODY_ANNOTATION_NAME) as? KtLambdaExpression)

    private fun getAnnotatedLambdaBlockInDeclaration(stateExpression: KtCallExpression) =
        (stateExpression.declaration?.bodyExpression?.findChildOfType<KtAnnotatedExpression>()?.baseExpression as? KtLambdaExpression)

    private fun getLambdaArgumentByArgumentName(stateExpression: KtCallExpression) =
        (stateExpression.argumentExpression(STATE_BODY_ARGUMENT_NAME) as? KtLambdaExpression)

    private fun buildAppends(state: State, statements: List<KtCallExpression>) = statements
        .filter { it.isAppendWithoutContext && !it.isAppendWithContext }
        .mapNotNull { appendExpression ->
            (appendExpression.argumentExpression(APPEND_OTHER_ARGUMENT_NAME) as? KtReferenceExpression)
                ?.let { Append(it, appendExpression, state) }
        }

    private fun buildStates(
        state: State,
        statements: List<KtCallExpression>,
        scenario: Scenario,
    ) = statements
        .filter { it.isState || it.isFallback || it.isAnnotatedWithStateDeclaration }
        .map { buildState(it, state, scenario) }
}


private fun KtCallExpression.identifierOfStateExpression(): StateIdentifier {
    return when {
        isAnnotatedWithStateDeclaration -> {
            val stateNameExpression = argumentExpressionsByAnnotation(STATE_NAME_ANNOTATION_NAME).firstOrNull()
            if (stateNameExpression != null) {
                return ExpressionIdentifier(stateNameExpression, stateNameExpression.parent)
            }

            val stateAnnotation = getMethodAnnotations(STATE_DECLARATION_ANNOTATION_NAME).last()

            val stateName = stateAnnotation.argumentExpression(STATE_NAME_ANNOTATION_ARGUMENT_NAME)?.stringValueOrNull

            return stateName?.let { PredefinedIdentifier(it, this) }
                ?: return NoIdentifier(
                    this,
                    "The state name is not defined. Use @$STATE_NAME_ANNOTATION_NAME or specify $STATE_NAME_ANNOTATION_ARGUMENT_NAME of @$STATE_DECLARATION_ANNOTATION_NAME."
                )
        }

        isScenario -> PredefinedIdentifier("", this)

        isCreateModelFun -> PredefinedIdentifier("", this)

        isFallback -> argumentExpression(STATE_NAME_ARGUMENT_NAME)?.let { ExpressionIdentifier(it, it.parent) }
            ?: PredefinedIdentifier("fallback", this)

        isState -> argumentExpression(STATE_NAME_ARGUMENT_NAME)?.let { ExpressionIdentifier(it, it.parent) }
            ?: NoIdentifier(this,
                "JAICF Plugin is not able to resolve state name. Specify $STATE_NAME_ARGUMENT_NAME parameter.")

        isAppendWithContext -> argumentExpression(APPEND_CONTEXT_ARGUMENT_NAME)
            ?.let { ExpressionIdentifier(it, it.parent) } ?: NoIdentifier(this,
            "JAICF Plugin is not able to resolve state name. Specify $APPEND_CONTEXT_ARGUMENT_NAME parameter.")

        else -> throw IllegalArgumentException("${this.text} is not a state declaration")
    }
}

private val KtCallExpression.isState: Boolean
    get() = isOverride(scenarioGraphBuilderClassFqName, STATE_METHOD_NAME)

private val KtCallExpression.isFallback: Boolean
    get() = isOverride(scenarioGraphBuilderClassFqName, FALLBACK_METHOD_NAME)

private val KtCallExpression.isAnnotatedWithStateDeclaration: Boolean
    get() = getMethodAnnotations(STATE_DECLARATION_ANNOTATION_NAME).isNotEmpty()

private val KtCallExpression.isScenario: Boolean
    get() = isOverride(ScenarioPackageFqName, SCENARIO_METHOD_NAME)

private val KtCallExpression.isCreateModelFun: Boolean
    get() = isOverride(ScenarioPackageFqName, CREATE_MODEL_METHOD_NAME)

private val KtCallExpression.isAppendWithoutContext: Boolean
    get() = valueArgument(APPEND_CONTEXT_ARGUMENT_NAME) == null &&
            (isOverride(scenarioGraphBuilderClassFqName, APPEND_METHOD_NAME) ||
                    isOverride(rootBuilderClassFqName, APPEND_METHOD_NAME) ||
                    isOverride(scenarioGraphBuilderClassFqName, DO_APPEND_METHOD_NAME))

private val KtCallExpression.isAppendWithContext: Boolean
    get() = valueArgument(APPEND_CONTEXT_ARGUMENT_NAME) != null &&
            (isOverride(scenarioGraphBuilderClassFqName, APPEND_METHOD_NAME) ||
                    isOverride(rootBuilderClassFqName, APPEND_METHOD_NAME))

val KtCallExpression.isStateDeclaration: Boolean
    get() = isState || isFallback || isScenario || isCreateModelFun || isAnnotatedWithStateDeclaration

private fun Scenario.allAppends() = topLevelAppends + innerState.allAppends()

private fun State.allAppends(): List<Append> = appends + states.flatMap { it.allAppends() }
