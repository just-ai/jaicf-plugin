package com.justai.jaicf.plugin.scenarios.transition

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker
import com.justai.jaicf.plugin.scenarios.JaicfService
import com.justai.jaicf.plugin.scenarios.linker.allStates
import com.justai.jaicf.plugin.scenarios.linker.appendingStates
import com.justai.jaicf.plugin.scenarios.linker.rootScenarios
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.isRootState
import com.justai.jaicf.plugin.scenarios.psi.dto.isTopState
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.NoState
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.OutOfStateBoundUsage
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.StateFound
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.StatesFound
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.SuggestionsFound
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.UnresolvedPath
import com.justai.jaicf.plugin.utils.isExist

/**
 * Сервис позволяющий найти все возможные переходы из стейта используя [Transition].
 * Так как плагин не всегда может точно знать какой именно сценарий будет передан в BotEngine, то есть некоторое
 * количество возможных вариантов перехода из одного стейта по одному transition.
 */
class TransitionService(project: Project) : JaicfService(project) {

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

            is Transition.StateId -> {
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
        fun getInstance(element: PsiElement): TransitionService? =
            if (element.isExist) getInstance(element.project)
            else null

        fun getInstance(project: Project): TransitionService =
            project.getService(TransitionService::class.java)
    }
}

fun State.transit(transition: Transition) = TransitionService.getInstance(project).transit(this, transition)

/**
 * Класс описывающий результат перехода из стейта.
 * [OutOfStateBoundUsage] - переданное выражение находится вне стейта. Используется в extensials методах
 * [UnresolvedPath] - переданный путь не может resolved. Используется в extensials методах
 * [NoState] - не найдено ни одного стейта
 * [StateFound] - найден только один подходящий стейт
 * [StatesFound] - найдено несколько подходящих стейтов. Такое происходит когда существует несколько вариантов appending сценариев
 * [SuggestionsFound] - не найден ни один стейт до которого можно добраться используя переданный Transition, но если добавить append то путь будет resolved
 */
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
