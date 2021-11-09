package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.StateIdentifier.NoIdentifier
import com.justai.jaicf.plugin.scenarios.psi.dto.identifierReference
import com.justai.jaicf.plugin.scenarios.psi.dto.isRootState
import com.justai.jaicf.plugin.scenarios.psi.dto.isTopState
import com.justai.jaicf.plugin.scenarios.psi.dto.name
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Slash
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition.Current
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition.Revert
import com.justai.jaicf.plugin.scenarios.transition.StatePath

class StateNameInspection : LocalInspectionTool() {

    override fun getID() = "StateNameInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = StateNameVisitor(holder)

    private class StateNameVisitor(holder: ProblemsHolder) : StateVisitor(holder) {

        override fun visitState(state: State) {
            if (state.identifier is NoIdentifier) {
                registerGenericError(state.stateExpression, state.identifier.errorCauseMessage)
                return
            }

            state.name?.let { inspectStateName(state, it) }
                ?: registerWarning(
                    state.identifierReference ?: state.stateExpression,
                    "JAICF Plugin is not able to resolve state name"
                )
        }

        private fun inspectStateName(state: State, name: String) {
            val parsedName = StatePath.parse(name)
            val identifierReference = state.identifierReference ?: return

            if (!state.isRootState && hasNoName(parsedName)) {
                registerGenericError(identifierReference, "State name must not be empty")
            }

            if (!state.isRootState && hasBlankName(parsedName)) {
                registerGenericError(identifierReference, "State name must not be blank")
            }

            if (state.isTopState && name.containsNonOpeningSlash) {
                registerGenericError(
                    identifierReference,
                    "Only single leading slash is allowed for top-level state. Resolved name \"$name\""
                )
            }

            if (!state.isTopState && !state.isRootState && parsedName.lexemes.contains(Slash)) {
                registerGenericError(
                    identifierReference, "Slashes are not allowed in inner states. Resolved name \"$name\""
                )
            }

            if (parsedName.transitions.contains(Current)) {
                registerGenericError(
                    identifierReference, "Do not use . as state name. Resolved name \"$name\""
                )
            }

            if (parsedName.transitions.contains(Revert)) {
                registerGenericError(
                    identifierReference, "Do not use .. as state name. Resolved name \"$name\""
                )
            }
        }

        private val String.containsNonOpeningSlash get() = lastIndexOf(Slash.identifier) > 0

        private fun hasNoName(parsedName: StatePath) = parsedName.transitions.all { it.identifier.isEmpty() }

        private fun hasBlankName(parsedName: StatePath) =
            parsedName.transitions.any { it is Transition.StateId && it.identifier.isBlank() }
    }
}
