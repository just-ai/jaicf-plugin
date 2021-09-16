package com.justai.jaicf.plugin.services.navigation

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker
import com.justai.jaicf.plugin.Lexeme.Transition
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.services.Service
import com.justai.jaicf.plugin.services.linter.allStates
import com.justai.jaicf.plugin.services.linter.appendingStates
import com.justai.jaicf.plugin.services.linter.rootScenarios
import com.justai.jaicf.plugin.services.managers.dto.State
import com.justai.jaicf.plugin.services.managers.dto.isRootState
import com.justai.jaicf.plugin.services.managers.dto.isTopState
import com.justai.jaicf.plugin.services.navigation.TransitionResult.NoState
import com.justai.jaicf.plugin.services.navigation.TransitionResult.StateFound
import com.justai.jaicf.plugin.services.navigation.TransitionResult.StatesFound
import com.justai.jaicf.plugin.services.navigation.TransitionResult.SuggestionsFound

class Navigator(project: Project) : Service(project) {

    private val cache by cached(PsiModificationTracker.MODIFICATION_COUNT) {
        mutableMapOf<Pair<State, Transition>, TransitionResult>()
    }

    fun transit(state: State, transition: Transition): TransitionResult {
        cache[state to transition]?.let { return it }

        return when (transition) {
            Transition.Current -> StateFound(state)

            Transition.Revert -> when {
                state.isRootState -> state.scenario.appendingStates
                    .let {
                        if (it.isEmpty()) StatesFound(it + state)
                        else StateFound(state)
                    }

                state.isTopState -> state.scenario.appendingStates
                    .let {
                        if (it.isEmpty()) StatesFound(it + state.parent!!)
                        else StateFound(state.parent!!)
                    }

                else -> StateFound(state.parent ?: state)
            }

            Transition.Root -> state.roots.let {
                if (it.size == 1) StateFound(it.first())
                else StatesFound(it)
            }

            is Transition.GoState -> {
                transition.transitToOneOf(state.allStates)
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
        operator fun get(element: PsiElement): Navigator? =
            if (element.isExist) get(element.project)
            else null

        operator fun get(project: Project): Navigator =
            project.getService(Navigator::class.java)
    }
}

fun State.transit(transition: Transition) = Navigator[project].transit(this, transition)
