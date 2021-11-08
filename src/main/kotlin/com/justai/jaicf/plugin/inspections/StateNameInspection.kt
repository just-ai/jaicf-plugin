package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Slash
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition.Current
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition.Revert
import com.justai.jaicf.plugin.scenarios.transition.StatePath
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.StateIdentifier
import com.justai.jaicf.plugin.scenarios.psi.dto.identifierReference
import com.justai.jaicf.plugin.scenarios.psi.dto.isRootState
import com.justai.jaicf.plugin.scenarios.psi.dto.isTopState
import com.justai.jaicf.plugin.scenarios.psi.dto.name

class StateNameInspection : LocalInspectionTool() {

    override fun getID() = "ForbiddenStateNameInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        ForbiddenStateNameVisitor(holder)

    private class ForbiddenStateNameVisitor(holder: ProblemsHolder) : StateVisitor(holder) {

        override fun visitState(visitedState: State) {
            if (visitedState.identifier is StateIdentifier.NoIdentifier) {
                registerGenericError(visitedState.stateExpression, visitedState.identifier.errorMessage)
                return
            }

            val stateName = visitedState.name
            if (stateName == null) {
                registerWarning(
                    visitedState.identifierReference ?: visitedState.stateExpression,
                    "JAICF Plugin is not able to resolve state name"
                )
                return
            }

            inspectStateIdentifier(visitedState, stateName)
        }

        private fun inspectStateIdentifier(state: State, stateName: String) {
            val parsedStateName = StatePath.parse(stateName)
            val identifierReference = state.identifierReference ?: return

            if (!state.isRootState && hasBlankNames(parsedStateName)) {
                registerGenericError(identifierReference, "State name must not be empty")
            }

            if (state.isTopState && isContainsSlashesInMiddle(stateName)) {
                registerGenericError(
                    identifierReference,
                    "Only single leading slash is allowed for top-level state. Resolved name \"$stateName\""
                )
            }

            if (!state.isTopState && !state.isRootState && parsedStateName.lexemes.contains(Slash)) {
                registerGenericError(
                    identifierReference, "Slashes are not allowed in inner states. Resolved name \"$stateName\""
                )
            }

            if (parsedStateName.transitions.contains(Current)) {
                registerGenericError(
                    identifierReference, "Do not use . in state name. Resolved name \"$stateName\""
                )
            }

            if (parsedStateName.transitions.contains(Revert)) {
                registerGenericError(
                    identifierReference, "Do not use .. in state name. Resolved name \"$stateName\""
                )
            }
        }

        private fun isContainsSlashesInMiddle(stateName: String) =
            stateName.lastIndexOf(Slash.identifier) > 0

        private fun hasBlankNames(parsedStateName: StatePath) =
            parsedStateName.transitions.none { it is Transition.GoState && it.identifier.isNotBlank() }
    }
}
