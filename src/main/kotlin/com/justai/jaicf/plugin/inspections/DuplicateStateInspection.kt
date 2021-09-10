package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.justai.jaicf.plugin.Lexeme.Transition.GoState
import com.justai.jaicf.plugin.Lexeme.Transition.Revert
import com.justai.jaicf.plugin.State
import com.justai.jaicf.plugin.allStates
import com.justai.jaicf.plugin.fullPath
import com.justai.jaicf.plugin.name
import com.justai.jaicf.plugin.nameReferenceExpression
import com.justai.jaicf.plugin.states
import com.justai.jaicf.plugin.transit
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

class DuplicateStateInspection : LocalInspectionTool() {

    override fun getID() = "DuplicateStateInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = DuplicateStateVisitor(holder)

    class DuplicateStateVisitor(holder: ProblemsHolder) : StateVisitor(holder) {

        override fun visitState(visitedState: State) {
            val stateName = visitedState.name ?: return
            val parents = visitedState.transit(Revert).states().filter { it !== visitedState }

            parents
                .flatMap { it.allStates() }
                .filter { it !== visitedState && it.name == stateName }
                .ifNotEmpty { registerGenericError(visitedState, this) }
        }

        private fun registerGenericError(state: State, duplicates: List<State>) {
            val holder = state.callExpression.nameReferenceExpression() ?: state.callExpression

            duplicates.forEach {
                registerGenericError(
                    holder,
                    "Duplicated state declaration found. Consider using different state names",
                    NavigateToState("Go to duplicate state declaration ${it.fullPath}", it)
                )
            }
        }
    }
}

private fun State.transitTo(stateName: String) = transit(GoState(stateName))
