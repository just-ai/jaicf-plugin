package com.justai.jaicf.plugin.services.navigation

import com.justai.jaicf.plugin.Lexeme.Transition
import com.justai.jaicf.plugin.Lexeme.Transition.Root
import com.justai.jaicf.plugin.StatePath
import com.justai.jaicf.plugin.plus
import com.justai.jaicf.plugin.services.linter.appendingStates
import com.justai.jaicf.plugin.services.locator.framingState
import com.justai.jaicf.plugin.services.managers.dto.State
import com.justai.jaicf.plugin.services.managers.dto.name
import com.justai.jaicf.plugin.services.managers.dto.root
import com.justai.jaicf.plugin.services.navigation.TransitionResult.NoState
import com.justai.jaicf.plugin.services.navigation.TransitionResult.OutOfStateBoundUsage
import com.justai.jaicf.plugin.services.navigation.TransitionResult.StateFound
import com.justai.jaicf.plugin.services.navigation.TransitionResult.StatesFound
import com.justai.jaicf.plugin.services.navigation.TransitionResult.SuggestionsFound
import com.justai.jaicf.plugin.services.navigation.TransitionResult.UnresolvedPath
import com.justai.jaicf.plugin.stringValueOrNull
import com.justai.jaicf.plugin.withoutLeadSlashes
import org.jetbrains.kotlin.psi.KtExpression

fun transitToState(pathExpression: KtExpression): TransitionResult {
    val framingState = pathExpression.framingState ?: return OutOfStateBoundUsage
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
        if (parent == null) {
            return StatePath(listOf(Root))
        }

        val parentPath = parent.absolutePath ?: return null
        val currentName = name ?: return null
        return parentPath + Transition.GoState(currentName.withoutLeadSlashes())
    }

val State.fullPath: String
    get() = scenario.name?.let { "$it:$absolutePath" } ?: "$absolutePath"
