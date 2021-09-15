package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.justai.jaicf.plugin.Lexeme.Transition
import com.justai.jaicf.plugin.StatePath
import com.justai.jaicf.plugin.services.locator.framingState
import com.justai.jaicf.plugin.services.managers.dto.State
import com.justai.jaicf.plugin.services.navigation.TransitionResult
import com.justai.jaicf.plugin.services.navigation.TransitionResult.NoState
import com.justai.jaicf.plugin.services.navigation.TransitionResult.OutOfStateBoundUsage
import com.justai.jaicf.plugin.services.navigation.TransitionResult.StateFound
import com.justai.jaicf.plugin.services.navigation.TransitionResult.StatesFound
import com.justai.jaicf.plugin.services.navigation.TransitionResult.SuggestionsFound
import com.justai.jaicf.plugin.services.navigation.TransitionResult.UnresolvedPath
import com.justai.jaicf.plugin.services.navigation.fullPath
import com.justai.jaicf.plugin.services.navigation.states
import com.justai.jaicf.plugin.services.navigation.transit
import com.justai.jaicf.plugin.services.navigation.transitToState
import com.justai.jaicf.plugin.stringValueOrNull
import org.jetbrains.kotlin.psi.KtExpression

class StatePathInspection : LocalInspectionTool() {

    override fun getID() = "StatePathInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = StatePathVisitor(holder)

    class StatePathVisitor(holder: ProblemsHolder) : PathExpressionVisitor(holder) {

        override fun visitPathExpression(pathExpression: KtExpression) {
            when (val transitToState = transitToState(pathExpression)) {
                UnresolvedPath -> registerWeakWarning(
                    pathExpression, "JAICF Plugin is not able to resolve the path"
                )

                NoState -> registerWeakWarning(
                    pathExpression, "No state with this path found"
                )

                is SuggestionsFound -> transitToState.suggestionsStates.forEach { suggestion ->
                    registerWeakWarning(
                        pathExpression,
                        "Found unrelated state ${suggestion.fullPath}",
                        NavigateToState("Go to unrelated state declaration ${suggestion.fullPath}", suggestion)
                    )
                }
            }
        }
    }
}

class MultiContextStatePathInspection : LocalInspectionTool() {

    override fun getID() = "MultiStatePathInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = StatePathVisitor(holder)

    class StatePathVisitor(holder: ProblemsHolder) : PathExpressionVisitor(holder) {

        override fun visitPathExpression(pathExpression: KtExpression) {
            val framingState = pathExpression.framingState ?: return
            val statePath = pathExpression.stringValueOrNull?.let { StatePath.parse(it) } ?: return

            if (framingState.transit(statePath).states().isEmpty())
                return

             val failedTransitions = collectFailedTransitions(framingState, statePath.transitions)

            failedTransitions
                .forEach { (state, _) ->
                    registerWeakWarning(
                        pathExpression,
                        "State resolved not in all contexts",
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
