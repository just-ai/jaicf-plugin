package com.justai.jaicf.plugin

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import com.justai.jaicf.plugin.Lexeme.Transition
import com.justai.jaicf.plugin.TransitionResult.NoState
import com.justai.jaicf.plugin.TransitionResult.OutOfStateBoundUsage
import com.justai.jaicf.plugin.TransitionResult.StateFound
import com.justai.jaicf.plugin.TransitionResult.SuggestionsFound
import com.justai.jaicf.plugin.TransitionResult.UnresolvedPath

private val logger = Logger.getInstance("StateNavigation")

fun PsiElement.getFramingState() =
    ServiceManager.getService(project, ScenarioService::class.java).getState(this)?.let {
        return@let if (it.isValid) {
            it
        } else {
            logger.warn("State is invalid. $it")
            null
        }
    }

fun State.absolutePath(): StatePath? {
    if (parent == null) {
        return StatePath(listOf(Transition.Root))
    }

    val parentPath = parent.absolutePath() ?: return null
    val currentName = identifier.resolveText() ?: return null
    return parentPath + Transition.GoState(currentName.withoutLeadSlashes())
}

fun transitToState(pathExpression: KtExpression): TransitionResult {
    val framingState = pathExpression.getFramingState() ?: return OutOfStateBoundUsage
    val statePath = pathExpression.stringValueOrNull?.let { StatePath.parse(it) } ?: return UnresolvedPath
    return framingState.transit(statePath)
}

fun State.transit(path: StatePath) = path.transitions
    .fold<Transition, TransitionResult>(StateFound(this)) { transitionResult, transition ->
        when (transitionResult) {
            is StateFound -> transitionResult.state.transit(transition)
            is SuggestionsFound -> transitionResult.suggestionsStates.map { it.transit(transition) }
                .filterIsInstance<StateFound>()
                .map { it.state }
                .let {
                    if (it.isEmpty()) NoState
                    else SuggestionsFound(it)
                }

            else -> return NoState
        }
    }

fun State.transit(transition: Transition) = when (transition) {
    Transition.Current -> StateFound(this)
    Transition.Revert -> StateFound(this.parent ?: this)
    Transition.Root -> StateFound(this.root)

    is Transition.GoState -> {
        transition.transitToOneOf(this.allStates())
            ?.let { StateFound(it) }
            ?: if (this.isRootState) {
                val rootScenarios = ServiceManager.getService(callExpression.project, ScenarioService::class.java)
                    .getRootScenarios()
                val suggestionsState = rootScenarios.flatMap { it.innerState.allStates() }
                    .filter { transition.canTransitTo(it) }

                if (suggestionsState.isNotEmpty())
                    SuggestionsFound(suggestionsState)
                else
                    NoState
            } else {
                NoState
            }
    }
}

sealed class TransitionResult {
    object OutOfStateBoundUsage : TransitionResult()
    object UnresolvedPath : TransitionResult()
    object NoState : TransitionResult()
    data class StateFound(val state: State) : TransitionResult()
    data class SuggestionsFound(val suggestionsStates: List<State>) : TransitionResult()
}

fun TransitionResult.firstStateOrSuggestion() = when (this) {
    is StateFound -> state
    is SuggestionsFound -> suggestionsStates.firstOrNull()
    else -> null
}

fun TransitionResult.statesOrSuggestions() = when (this) {
    is StateFound -> listOf(state)
    is SuggestionsFound -> suggestionsStates
    else -> emptyList()
}

fun findStateUsages(state: State): List<KtExpression> {
    var methods: List<PsiNameIdentifierOwner> = getAllAnnotatedMethodsDeclarations(state.callExpression.project)
    if (methods.isEmpty())
        methods = getAllJumpMethodsDeclarations(state.callExpression.project)

    if (methods.isEmpty())
        return emptyList()

    val allPathExpressions = methods
        .flatMap { ReferencesSearch.search(it, state.callExpression.project.projectScope()).findAll() }
        .map { it.element }
        .flatMap { it.getPathExpressionsOfBoundedBlock() }

    if (allPathExpressions.isEmpty())
        Logger.getInstance("findStateUsages").warn("No path expression was found in project")

    return allPathExpressions
        .filter { transitToState(it).firstStateOrSuggestion() == state }
}

private fun getAllJumpMethodsDeclarations(project: Project): List<PsiMethod> {
    check(!DumbService.getInstance(project).isDumb) { "IDEA in dumb mode" }

    val reactionsClass = findClass(REACTIONS_PACKAGE, REACTIONS_CLASS_NAME, project) ?: return emptyList()
    val jumpMethods = reactionsClass.allMethods.filter {
        it.name == REACTIONS_GO_METHOD_NAME || it.name == REACTIONS_CHANGE_STATE_METHOD_NAME
    }

    if (jumpMethods.isEmpty()) {
        logger.warn("No jump method declaration was found")
        return emptyList()
    }

    return jumpMethods
}

private fun getAllAnnotatedMethodsDeclarations(project: Project): List<KtFunction> {
    val pathValueAnnotation = findClass(PLUGIN_PACKAGE, PATH_ARGUMENT_ANNOTATION_NAME, project) ?: return emptyList()
    val annotatedMethods = ReferencesSearch.search(pathValueAnnotation, project.projectScope())
        .toList()
        .mapNotNull { it.element.getParentOfType<KtFunction>(true) }
        .distinct()

    if (annotatedMethods.isEmpty()) {
        logger.info("No annotated method was found")
    }

    return annotatedMethods
}

internal fun String.withoutLeadSlashes() =
    if (contains('/'))
        this.dropWhile { it == '/' }
    else
        this
