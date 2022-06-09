package com.justai.jaicf.plugin.core.transition

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker
import com.justai.jaicf.plugin.core.JaicfService
import com.justai.jaicf.plugin.core.linker.allStates
import com.justai.jaicf.plugin.core.linker.appendingStates
import com.justai.jaicf.plugin.core.linker.rootScenarios
import com.justai.jaicf.plugin.core.linker.sequenceAllStates
import com.justai.jaicf.plugin.core.psi.dto.State
import com.justai.jaicf.plugin.core.psi.dto.isRootState
import com.justai.jaicf.plugin.core.psi.dto.isTopState
import com.justai.jaicf.plugin.core.psi.dto.name
import com.justai.jaicf.plugin.core.transition.Lexeme.Transition
import com.justai.jaicf.plugin.core.transition.TransitionResult.NoState
import com.justai.jaicf.plugin.core.transition.TransitionResult.StateFound
import com.justai.jaicf.plugin.core.transition.TransitionResult.StatesFound
import com.justai.jaicf.plugin.core.transition.TransitionResult.SuggestionsFound
import com.justai.jaicf.plugin.utils.isExist
import com.justai.jaicf.plugin.utils.measure

class TransitionService(project: Project) : JaicfService(project) {

    private val cache by cached(PsiModificationTracker.MODIFICATION_COUNT) {
        mutableMapOf<Pair<State, Transition>, TransitionResult>()
    }

    fun transit(state: State, transition: Transition): TransitionResult {
        cache[state to transition]?.let { return it }

        return when (transition) {
            Transition.Current -> StateFound(state)

            Transition.StepUp -> when {
                state.isRootState -> state.scenario.appendingStates
                    .let {
                        if (it.isNotEmpty()) StatesFound(it + state)
                        else StateFound(state)
                    }

                state.isTopState -> state.scenario.appendingStates
                    .let {
                        if (it.isNotEmpty()) StatesFound(it + state.parent!!)
                        else StateFound(state.parent!!)
                    }

                else -> StateFound(state.parent ?: state)
            }

            Transition.Root -> state.roots.let {
                if (it.size == 1) StateFound(it.first())
                else StatesFound(it)
            }

            is Transition.StateId -> {
                transition.transitToOneOf(state.sequenceAllStates)
                    ?.let { StateFound(it) }
                    ?: if (state.isRootState) {
                        val rootScenarios = state.project.rootScenarios
                        val suggestionsState = rootScenarios
                            .flatMap { it.innerState.allStates }
                            .filter { transition.canTransitTo(it) }

                        if (suggestionsState.isNotEmpty())
                            SuggestionsFound(suggestionsState)
                        else
                            NoState
                    } else {
                        NoState
                    }
            }
        }.also {
            cache[state to transition] = it
        }
    }

    companion object {
        fun getInstance(element: PsiElement): TransitionService? =
            if (element.isExist) getInstance(element.project)
            else null

        fun getInstance(project: Project): TransitionService =
            project.getService(TransitionService::class.java)
    }
}

fun State.transit(transition: Transition) = project.measure("State(${name}).transit(${transition.javaClass.simpleName})") {
    TransitionService.getInstance(project).transit(this@transit, transition)
}

sealed class TransitionResult {
    object OutOfStateBoundUsage : TransitionResult()
    object UnresolvedPath : TransitionResult()
    object NoState : TransitionResult()
    data class StateFound(val state: State) : TransitionResult()
    data class StatesFound(val states: List<State>) : TransitionResult()
    data class SuggestionsFound(val suggestionsStates: List<State>) : TransitionResult()
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
