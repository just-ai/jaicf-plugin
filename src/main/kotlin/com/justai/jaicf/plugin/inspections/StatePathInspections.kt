package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtExpression
import com.justai.jaicf.plugin.TransitionResult
import com.justai.jaicf.plugin.absolutePath
import com.justai.jaicf.plugin.identifierReference
import com.justai.jaicf.plugin.transitToState

class StatePathInspection : LocalInspectionTool() {

    override fun getID() = "StatePathInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = StatePathVisitor(holder)

    class StatePathVisitor(holder: ProblemsHolder) : PathExpressionVisitor(holder) {

        override fun visitPathExpression(pathExpression: KtExpression) {
            when (val transitToState = transitToState(pathExpression)) {
                TransitionResult.UnresolvedPath -> registerWeakWarning(
                    pathExpression, "JAICF Plugin is not able to resolve the path"
                )

                TransitionResult.NoState -> registerWeakWarning(
                    pathExpression, "No state with this path found"
                )

                is TransitionResult.SuggestionsFound -> transitToState.suggestionsStates.forEach { suggestion ->
                    registerWeakWarning(
                        pathExpression,
                        "Found unrelated state ${suggestion.absolutePath()}",
                        NavigateToState(
                            "Go to unrelated state declaration  ${suggestion.identifierReference?.text}",
                            suggestion
                        )
                    )
                }
            }
        }
    }
}
