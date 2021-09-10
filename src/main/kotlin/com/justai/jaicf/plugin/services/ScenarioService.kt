package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
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
import com.justai.jaicf.plugin.RecursiveSafeValue
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
import com.justai.jaicf.plugin.Scenario
import com.justai.jaicf.plugin.Scenario.Condition.ACTUAL
import com.justai.jaicf.plugin.Scenario.Condition.CORRUPTED
import com.justai.jaicf.plugin.Scenario.Condition.MODIFIED
import com.justai.jaicf.plugin.ScenarioPackageFqName
import com.justai.jaicf.plugin.State
import com.justai.jaicf.plugin.StateIdentifier
import com.justai.jaicf.plugin.StateIdentifier.ExpressionIdentifier
import com.justai.jaicf.plugin.StateIdentifier.NoIdentifier
import com.justai.jaicf.plugin.StateIdentifier.PredefinedIdentifier
import com.justai.jaicf.plugin.actualCondition
import com.justai.jaicf.plugin.argumentExpression
import com.justai.jaicf.plugin.argumentExpressionsByAnnotation
import com.justai.jaicf.plugin.argumentExpressionsOrDefaultValuesByAnnotation
import com.justai.jaicf.plugin.contains
import com.justai.jaicf.plugin.declaration
import com.justai.jaicf.plugin.findChildOfType
import com.justai.jaicf.plugin.findClass
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
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.util.firstNotNullResult

class ScenarioService(project: Project) : Service {

    private val modifiedFiles: MutableSet<KtFile> = mutableSetOf()
    private val builder: ScenarioBuilder = ScenarioBuilder(project)
    private val scenariosByFiles by lazy {
        RecursiveSafeValue(builder.buildScenarios(project.projectScope())) {
            updateAllScenariosIfNeeded(it)
        }
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

        return scenariosByFiles.value[file.originalFile]
            ?.filter { it.actualCondition == ACTUAL }
            ?: return emptyList()
    }

    fun getRootScenarios(): List<Scenario> {
        val notRootScenarios =
            scenariosByFiles.value.values
                .flatten()
                .flatMap(Scenario::allAppends)
                .mapNotNull { (append, _) -> append.resolve() }

        return (scenariosByFiles.value.values.flatten() - notRootScenarios)
            .filter { it.actualCondition == ACTUAL }
    }

    override fun markFileAsModified(file: KtFile) {
        modifiedFiles += file
        scenariosByFiles.invalid()
    }

    fun getScenarioName(scenario: Scenario): String? = builder.getScenarioName(scenario)

    fun getAppendingStates(scenario: Scenario): List<State> {
        return scenariosByFiles.value.values.flatten()
            .flatMap { it.allAppends }
            .filter { (append, _) -> append.resolve() == scenario }
            .map { it.second }
    }

    private fun updateScenariosConditions(scenarios: List<Scenario>) {
        scenarios.forEach { scenario ->
            when {
                scenario.innerState.callExpression.isRemoved || scenario.declarationElement.isRemoved -> {
                    scenario.removed()
                    scenariosByFiles.invalid()
                }

                scenario.innerState.textHashCode != scenario.innerState.callExpression.text.hashCode() -> {
                    scenario.modified()
                    scenariosByFiles.invalid()
                }
            }
        }
    }

    private fun updateAllScenariosIfNeeded(map: Map<KtFile, List<Scenario>>): Map<KtFile, List<Scenario>> {
        if (modifiedFiles.isEmpty())
            return map

        val files = (modifiedFiles + map.keys).distinct()
        return files
            .map { it to (map[it] ?: emptyList()) }
            .map { (file, scenarios) ->
                if (!modifiedFiles.contains(file))
                    return@map file to scenarios

                updateScenariosConditions(scenarios)

                val actualScenarios = scenarios
                    .filter { it.condition == MODIFIED || it.condition == CORRUPTED }
                    .onEach { builder.rebuildScenario(it) }
                    .filter { it.condition == ACTUAL }

                val newScenarios = tryToFindNewScenarios(file, actualScenarios)

                file to actualScenarios + newScenarios.also {
                    modifiedFiles.remove(file)
                }
            }
            .filter { it.second.isNotEmpty() }
            .toMap()
    }

    private fun tryToFindNewScenarios(file: KtFile, existedScenarios: List<Scenario>): List<Scenario> =
        builder.getScenariosCallExpressions(file.fileScope())
            .filter { existedScenarios.none { scenario -> scenario.innerState.callExpression == it } }
            .mapNotNull { builder.buildScenario(it) }
            .filter { it.innerState.callExpression.isExist }

    private fun recursiveFindState(state: State, element: PsiElement): State =
        state.states.firstOrNull { contains(it, element) }?.let { recursiveFindState(it, element) } ?: state

    companion object {
        operator fun get(element: PsiElement): ScenarioService? =
            if (element.isExist) ServiceManager.getService(element.project, ScenarioService::class.java)
            else null
    }
}

private class ScenarioBuilder(val project: Project) {

    private val scenariosBuildMethods: List<PsiMethod> by lazy { getScenariosBuildMethodsDeclarations() }

    fun buildScenarios(scope: GlobalSearchScope): Map<KtFile, List<Scenario>> {
        if (DumbService.getInstance(project).isDumb)
            throw ProcessCanceledException()

        return getScenariosCallExpressions(scope)
            .mapNotNull { buildScenario(it) }
            .groupBy(Scenario::file)
    }

    fun buildScenario(body: KtCallExpression) =
        Scenario(body.containingKtFile).run {
            val state = StateBuilder.buildState(body, this) ?: return@run null
            innerState = state
            this
        }

    fun rebuildScenario(scenario: Scenario) {
        StateBuilder.buildState(scenario.innerState.callExpression, scenario)
            ?.let {
                scenario.innerState = it
                scenario.actual()
            }
            ?: run {
                scenario.corrupted()
            }
    }

    fun getScenariosCallExpressions(scope: GlobalSearchScope) = scenariosBuildMethods
        .flatMap { ReferencesSearch.search(it, scope, true).findAll() }
        .map { it.element.parent }
        .filterIsInstance<KtCallExpression>()

    fun getScenarioBody(expr: KtReferenceExpression): KtExpression? {
        if (expr.isRemoved)
            return null

        return when (val resolvedElement = expr.resolve()) {
            is KtObjectDeclaration ->
                resolvedElement.declarations
                    .filter { it.name == SCENARIO_MODEL_FIELD_NAME && it is KtProperty }
                    .map { it as KtProperty }
                    .mapNotNull { it.initializer ?: it.delegateExpression }
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

    fun getScenarioName(scenario: Scenario): String? {
        val scenarioDeclaration = scenario.declarationElement as? KtCallExpression ?: return null
        val parent = scenarioDeclaration.parent ?: return null
        (parent as? KtNamedFunction)?.let { return it.name }

        val property = scenarioDeclaration.parent as? KtProperty ?: return null
        return when {
            scenarioDeclaration.isScenario -> property.name
            scenarioDeclaration.isCreateModelFun -> property.getParentOfType<KtClassOrObject>(false)?.name
            else -> null
        }
    }

    private fun getScenariosBuildMethodsDeclarations(): List<PsiMethod> =
        findClass(SCENARIO_PACKAGE, SCENARIO_EXTENSIONS_CLASS_NAME, project)?.allMethods
            ?.filter { it.name == SCENARIO_METHOD_NAME || it.name == CREATE_MODEL_METHOD_NAME } ?: emptyList()
}

private object StateBuilder {

    private val logger = Logger.getInstance(StateBuilder::class.java)

    fun buildState(
        stateExpression: KtCallExpression,
        scenario: Scenario,
        parentState: State? = null,
    ): State? {
        if (stateExpression.isRemoved) {
            logger.warn("${stateExpression.text} is removed")
            return null
        }

        if (!stateExpression.isStateDeclaration) {
            logger.warn("${stateExpression.text} is not a state")
            return null
        }

        val identifier = stateExpression.identifierOfStateExpression()
        val statements = extractStatements(stateExpression)

        return State(identifier, parentState, scenario, stateExpression)
            .apply {
                appends = buildAppends(this, statements)
                states = buildStates(this, statements, scenario)
                textHashCode = stateExpression.text.hashCode()
            }
    }

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
        .filter(this::isState)
        .mapNotNull { buildState(it, scenario, state) }

    private fun extractStatements(stateExpression: KtCallExpression): List<KtCallExpression> {
        val bodyArgument =
            if (VersionService.isAnnotationsSupported(stateExpression.project))
                getAnnotatedLambdaArgument(stateExpression) ?: getAnnotatedLambdaBlockInDeclaration(stateExpression)
            else
                getLambdaArgumentByArgumentName(stateExpression)

        return bodyArgument?.bodyExpression?.statements?.mapNotNull { it as? KtCallExpression } ?: emptyList()
    }

    private fun getAnnotatedLambdaArgument(stateExpression: KtCallExpression) =
        (stateExpression.argumentExpressionsOrDefaultValuesByAnnotation(STATE_BODY_ANNOTATION_NAME)
            .firstOrNull() as? KtLambdaExpression)

    private fun getAnnotatedLambdaBlockInDeclaration(stateExpression: KtCallExpression) =
        (stateExpression.declaration?.bodyExpression?.findChildOfType<KtAnnotatedExpression>()?.baseExpression as? KtLambdaExpression)

    private fun getLambdaArgumentByArgumentName(stateExpression: KtCallExpression) =
        (stateExpression.argumentExpression(STATE_BODY_ARGUMENT_NAME) as? KtLambdaExpression)

    private fun isState(expression: KtCallExpression) =
        if (VersionService.isAnnotationsSupported(expression.project)) expression.isAnnotatedWithStateDeclaration
        else expression.isState || expression.isFallback
}

val KtCallExpression.isStateDeclaration: Boolean
    get() =
        if (VersionService.isAnnotationsSupported(project)) isAnnotatedWithStateDeclaration
        else isState || isFallback || isScenario || isCreateModelFun

private fun KtCallExpression.identifierOfStateExpression(): StateIdentifier {
    return if (VersionService.isAnnotationsSupported(project)) {
        val stateNameExpression = argumentExpressionsByAnnotation(STATE_NAME_ANNOTATION_NAME).firstOrNull()
        if (stateNameExpression != null) {
            return ExpressionIdentifier(stateNameExpression, stateNameExpression.parent)
        }

        val defaultExpression = argumentExpressionsOrDefaultValuesByAnnotation(STATE_NAME_ANNOTATION_NAME).firstOrNull()
        if (defaultExpression != null) {
            return ExpressionIdentifier(defaultExpression, defaultExpression.parent)
        }

        val stateAnnotation = getMethodAnnotations(STATE_DECLARATION_ANNOTATION_NAME).last()

        val stateName = stateAnnotation.argumentExpression(STATE_NAME_ANNOTATION_ARGUMENT_NAME)?.stringValueOrNull

        return stateName?.let { PredefinedIdentifier(it, this) }
            ?: return NoIdentifier(
                this,
                "The state name is not defined. Use @$STATE_NAME_ANNOTATION_NAME or specify $STATE_NAME_ANNOTATION_ARGUMENT_NAME of @$STATE_DECLARATION_ANNOTATION_NAME."
            )
    } else {
        when {
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
}

val Scenario.name: String?
    get() = ScenarioService[declarationElement]?.getScenarioName(this)

val Scenario.nestedStates: List<State>
    get() = innerState.nestedStates + innerState

private val Scenario.allAppends: List<Pair<Append, State>>
    get() = nestedStates.flatMap { state -> state.appends.map { it to state } } + topLevelAppends.map { it to innerState }

private val State.nestedStates: List<State>
    get() = states + states.flatMap(State::nestedStates)

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
