package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.justai.jaicf.plugin.State
import com.justai.jaicf.plugin.absolutePath
import com.justai.jaicf.plugin.allStates
import com.justai.jaicf.plugin.identifierReference
import com.justai.jaicf.plugin.name
import com.justai.jaicf.plugin.nameReferenceExpression
import com.justai.jaicf.plugin.services.name
import com.justai.jaicf.plugin.withoutLeadSlashes

class DuplicateStateInspection : LocalInspectionTool() {

    override fun getID() = "DuplicateStateInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = DuplicateStateVisitor(holder)

    class DuplicateStateVisitor(holder: ProblemsHolder) : StateVisitor(holder) {

        override fun visitState(visitedState: State) {
            visitedState.allStates()
                .duplicates
                .forEach(this::registerGenericError)
        }

        private fun registerGenericError(duplicates: List<State>) {
            duplicates
                .flatMap { (duplicates - it).map { duplicate -> it to duplicate } }
                .forEach { (state, duplicate) ->
                    registerGenericError(
                        state.callExpression.nameReferenceExpression() ?: state.callExpression,
                        "Duplicated state declaration found. Consider using different state names",
                        NavigateToState("Go to duplicate state declaration ${duplicate.scenario.name}:${duplicate.absolutePath}", duplicate)
                    )
                }
        }

        private val List<State>.duplicates
            get() = groupBy { it.name?.withoutLeadSlashes() }
                .filter { it.key != null && it.value.size > 1 }
                .map { it.value }
    }
}
