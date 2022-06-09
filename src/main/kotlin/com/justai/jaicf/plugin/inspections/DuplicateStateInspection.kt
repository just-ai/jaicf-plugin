package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.justai.jaicf.plugin.core.linker.allStates
import com.justai.jaicf.plugin.core.psi.dto.State
import com.justai.jaicf.plugin.core.psi.dto.nameWithoutLeadSlashes
import com.justai.jaicf.plugin.core.transition.Lexeme.Transition.StepUp
import com.justai.jaicf.plugin.core.transition.fullPath
import com.justai.jaicf.plugin.core.transition.states
import com.justai.jaicf.plugin.core.transition.transit
import com.justai.jaicf.plugin.utils.nameReferenceExpression
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

class DuplicateStateInspection : LocalInspectionTool() {

    override fun getID() = "DuplicateStateInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = DuplicateStateVisitor(holder)

    class DuplicateStateVisitor(holder: ProblemsHolder) : StateVisitor(holder) {

        override fun visitState(state: State) {
            val stateName = state.nameWithoutLeadSlashes ?: return
            val parents = state.transit(StepUp).states().filter { it !== state }

            parents
                .flatMap { it.allStates }
                .filter { it !== state && it.nameWithoutLeadSlashes == stateName }
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
