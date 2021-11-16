package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.justai.jaicf.plugin.scenarios.linker.framingState
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition
import com.justai.jaicf.plugin.scenarios.transition.StatePath
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.NoState
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.OutOfStateBoundUsage
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.StateFound
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.StatesFound
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.SuggestionsFound
import com.justai.jaicf.plugin.scenarios.transition.TransitionResult.UnresolvedPath
import com.justai.jaicf.plugin.scenarios.transition.fullPath
import com.justai.jaicf.plugin.scenarios.transition.states
import com.justai.jaicf.plugin.scenarios.transition.transit
import com.justai.jaicf.plugin.scenarios.transition.transitToState
import com.justai.jaicf.plugin.utils.StatePathExpression
import com.justai.jaicf.plugin.utils.StatePathExpression.Joined
import com.justai.jaicf.plugin.utils.holderExpression
import com.justai.jaicf.plugin.utils.stringValueOrNull

class StatePathInspection : LocalInspectionTool() {

    override fun getID() = "StatePathInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = StatePathVisitor(holder)

    class StatePathVisitor(holder: ProblemsHolder) : PathExpressionVisitor(holder) {

        override fun visitPathExpression(pathExpression: StatePathExpression) {
            when (val transitionResult = transitToState(pathExpression.usePoint, pathExpression.declaration)) {
                UnresolvedPath -> if (pathExpression is Joined)
                    registerWeakWarning(
                        pathExpression.holderExpression, "JAICF Plugin is not able to resolve the path"
                    )

                NoState -> registerWeakWarning(
                    pathExpression.holderExpression, "No state with this path found"
                )

                is SuggestionsFound -> transitionResult.suggestionsStates.forEach { suggestion ->
                    registerWeakWarning(
                        pathExpression.holderExpression,
                        "Found unrelated state ${suggestion.fullPath}",
                        NavigateToState("Go to unrelated state declaration ${suggestion.fullPath}", suggestion)
                    )
                }
            }
        }
    }
}

class MultiContextStatePathInspection : LocalInspectionTool() {

    override fun getID() = "MultiContextStatePathInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = StatePathVisitor(holder)

    class StatePathVisitor(holder: ProblemsHolder) : PathExpressionVisitor(holder) {

        override fun visitPathExpression(pathExpression: StatePathExpression) {
            val framingState = pathExpression.usePoint.framingState ?: return
            val statePath = pathExpression.declaration.stringValueOrNull?.let { StatePath.parse(it) } ?: return

            if (framingState.transit(statePath).states().isEmpty())
                return

            val failedTransitions = collectFailedTransitions(framingState, statePath.transitions)

            failedTransitions
                .forEach { (state, _) ->
                    registerWeakWarning(
                        pathExpression.holderExpression,
                        "State path is not resolved in all contexts",
                        NavigateToState("Go to unrelated state declaration ${state.fullPath}", state)
                    )
                }
        }

        private fun collectFailedTransitions(
            state: State,
            transitions: List<Transition>,
        ): List<Pair<State, TransitionResult>> {
            if (transitions.isEmpty())
                return emptyList()

            return when (val transitionResult = state.transit(transitions.first())) {
                is StateFound -> collectFailedTransitions(transitionResult.state, transitions.drop(1))
                is StatesFound -> transitionResult.states.flatMap { collectFailedTransitions(it, transitions.drop(1)) }
                is SuggestionsFound, NoState -> listOf(state to transitionResult)
                OutOfStateBoundUsage, UnresolvedPath -> emptyList()
            }
        }
    }
}
