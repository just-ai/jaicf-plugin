package com.justai.jaicf.plugin

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.Lexeme.Transition
import com.justai.jaicf.plugin.TransitionResult.NoState
import com.justai.jaicf.plugin.TransitionResult.OutOfStateBoundUsage
import com.justai.jaicf.plugin.TransitionResult.StateFound
import com.justai.jaicf.plugin.TransitionResult.StatesFound
import com.justai.jaicf.plugin.TransitionResult.SuggestionsFound
import com.justai.jaicf.plugin.TransitionResult.UnresolvedPath
import com.justai.jaicf.plugin.services.ScenarioService
import org.jetbrains.kotlin.psi.KtExpression

private val logger = Logger.getInstance("StateNavigation")

fun PsiElement.getFramingState(): State? {
    if (isRemoved)
        return null

    return ServiceManager.getService(project, ScenarioService::class.java).getState(this)?.let {
        return@let if (it.isValid) {
            it
        } else {
            logger.warn("State is invalid. $it")
            null
        }
    }
}

val State.absolutePath: StatePath?
    get() {
        if (parent == null) {
            return StatePath(listOf(Transition.Root))
        }

        val parentPath = parent.absolutePath ?: return null
        val currentName = name ?: return null
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

            is StatesFound -> transitionResult.states
                .map { it.transit(transition) }
                .filter { it is StateFound || it is StatesFound }
                .flatMap { it.states() }
                .let {
                    when {
                        it.isEmpty() -> NoState
                        it.size == 1 -> StateFound(it.first())
                        else -> StatesFound(it)
                    }
                }

            is SuggestionsFound -> transitionResult.suggestionsStates
                .map { it.transit(transition) }
                .filterIsInstance<StateFound>()
                .map { it.state }
                .let {
                    if (it.isEmpty()) NoState
                    else SuggestionsFound(it)
                }

            NoState -> return NoState

            OutOfStateBoundUsage -> return NoState

            UnresolvedPath -> return NoState
        }
    }

fun State.transit(transition: Transition) = when (transition) {
    Transition.Current -> StateFound(this)

    Transition.Revert -> {
        if (isRootState) {
            ScenarioService[callExpression]?.getAppendingStates(scenario)
                ?.let { StatesFound(it + this) }
                ?: StateFound(this)
        } else {
            StateFound(this.parent ?: this)
        }
    }

    Transition.Root -> roots.let {
        if (it.size == 1) StateFound(it.first())
        else StatesFound(it)
    }

    is Transition.GoState -> {
        transition.transitToOneOf(this.allStates())
            ?.let { StateFound(it) }
            ?: if (this.isRootState) {
                val rootScenarios = ScenarioService[callExpression]?.getRootScenarios() ?: emptyList()
                val suggestionsState = rootScenarios
                    .flatMap { it.innerState.allStates() }
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
    data class StatesFound(val states: List<State>) : TransitionResult()
    data class SuggestionsFound(val suggestionsStates: List<State>) : TransitionResult()
}

fun TransitionResult.firstStateOrSuggestion() = when (this) {
    is StateFound -> state
    is StatesFound -> states.firstOrNull()
    is SuggestionsFound -> suggestionsStates.firstOrNull()
    else -> null
}

fun TransitionResult.statesOrSuggestions() = when (this) {
    is StateFound -> listOf(state)
    is StatesFound -> states
    is SuggestionsFound -> suggestionsStates
    else -> emptyList()
}

fun TransitionResult.states() = when (this) {
    is StateFound -> listOf(state)
    is StatesFound -> states
    else -> emptyList()
}


internal fun String.withoutLeadSlashes() =
    if (contains('/'))
        this.dropWhile { it == '/' }
    else
        this

val State.roots: List<State>
    get() {
        val appendingStates = ScenarioService[callExpression]?.getAppendingStates(scenario) ?: emptyList()

        return if (appendingStates.isNotEmpty()) appendingStates.flatMap { it.roots } else listOf(root)
    }
