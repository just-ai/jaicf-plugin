package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.justai.jaicf.plugin.nameReferenceExpression
import com.justai.jaicf.plugin.scenarios.linter.allStates
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.name
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition.StateId
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition.Revert
import com.justai.jaicf.plugin.scenarios.transition.fullPath
import com.justai.jaicf.plugin.scenarios.transition.states
import com.justai.jaicf.plugin.scenarios.transition.transit
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

class DuplicateStateInspection : LocalInspectionTool() {

    override fun getID() = "DuplicateStateInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = DuplicateStateVisitor(holder)

    class DuplicateStateVisitor(holder: ProblemsHolder) : StateVisitor(holder) {

        override fun visitState(state: State) {
            val stateName = state.name ?: return
            val parents = state.transit(Revert).states().filter { it !== state }

            parents
                .flatMap { it.allStates }
                .filter { it !== state && it.name == stateName }
                .ifNotEmpty { registerGenericError(state, this) }
        }

        private fun registerGenericError(state: State, duplicates: List<State>) {
            val holder = state.stateExpression.let { it.nameReferenceExpression() ?: it }

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
