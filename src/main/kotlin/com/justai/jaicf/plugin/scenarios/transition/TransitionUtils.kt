package com.justai.jaicf.plugin.scenarios.transition

import com.justai.jaicf.plugin.scenarios.linker.appendingStates
import com.justai.jaicf.plugin.scenarios.linker.framingState
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.name
import com.justai.jaicf.plugin.scenarios.psi.dto.nameWithoutLeadSlashes
import com.justai.jaicf.plugin.scenarios.psi.dto.root
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition.Root
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.NoState
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.OutOfStateBoundUsage
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.StateFound
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.StatesFound
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.SuggestionsFound
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.UnresolvedPath
import com.justai.jaicf.plugin.utils.stringValueOrNull
import org.jetbrains.kotlin.psi.KtExpression

fun transitToState(usePointExpression: KtExpression, pathExpression: KtExpression): TransitionResult {
    val framingState = usePointExpression.framingState ?: return OutOfStateBoundUsage
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
                .distinct()
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
                .distinct()
                .let {
                    if (it.isEmpty()) NoState
                    else SuggestionsFound(it)
                }

            NoState -> return NoState

            OutOfStateBoundUsage -> return NoState

            UnresolvedPath -> return NoState
        }
    }

val State.roots: List<State>
    get() {
        val appendingStates = scenario.appendingStates

        return if (appendingStates.isNotEmpty()) appendingStates.flatMap { it.roots }
        else listOf(root)
    }

val State.absolutePath: StatePath?
    get() {
        if (parent == null) return StatePath(listOf(Root))

        val parentPath = parent.absolutePath ?: return null
        val currentName = nameWithoutLeadSlashes ?: return null
        return parentPath + Transition.StateId(currentName)
    }

val State.fullPath: String
    get() = scenario.name?.let { "$it:$absolutePath" } ?: "$absolutePath"
