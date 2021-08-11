package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.justai.jaicf.plugin.State
import com.justai.jaicf.plugin.allStates
import com.justai.jaicf.plugin.identifierReference
import com.justai.jaicf.plugin.nameReferenceExpression
import com.justai.jaicf.plugin.same

class DuplicateStateInspection : LocalInspectionTool() {

    override fun getID() = "DuplicateStateInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = DuplicateStateVisitor(holder)

    class DuplicateStateVisitor(holder: ProblemsHolder) : StateVisitor(holder) {

        override fun visitState(visitedState: State) {
            getDuplicates(visitedState)
                .onEach {
                    registerGenericError(
                        visitedState.callExpression.nameReferenceExpression() ?: visitedState.callExpression,
                        "Duplicated state declaration found. Consider using different state names",
                        NavigateToState("Go to duplicate state declaration ${it.identifierReference?.text}", it)
                    )
                }
        }

        private fun getDuplicates(visitedState: State): List<State> {
            val states = visitedState.parent?.allStates() ?: return emptyList()

            if (states.isEmpty()) {
                logger.error("state.parent.allStates is empty. ${visitedState.parent}")
                return emptyList()
            }

            return states
                .filter { it !== visitedState }
                .filter { visitedState.identifier same it.identifier }
        }
    }

    companion object {
        private val logger = Logger.getInstance(this::class.java)
    }
}
