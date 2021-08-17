package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.justai.jaicf.plugin.State
import com.justai.jaicf.plugin.getInnerPathExpressions
import com.justai.jaicf.plugin.isRemoved
import com.justai.jaicf.plugin.isValid
import com.justai.jaicf.plugin.services.ScenarioService
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

abstract class StateVisitor(holder: ProblemsHolder) : VisitorBase(holder) {

    abstract fun visitState(visitedState: State)

    override fun visitFile(file: PsiFile) {
        if (file !is KtFile)
            return

        val service = getScenarioService(file)

        service.getScenarios(file).forEach {
            if (it.declarationElement.isRemoved) {
                logger.warn("Scenario is removed. $it")
                it.removed()
                return@forEach
            }

            recursiveEntryIntoState(it.innerState)
        }
    }

    private fun recursiveEntryIntoState(state: State) {
        if (!state.isValid) {
            logger.warn("State is removed. $state")
            return
        }

        visitState(state)
        state.states.forEach { recursiveEntryIntoState(it) }
    }

    private fun getScenarioService(file: KtFile) = ServiceManager.getService(file.project, ScenarioService::class.java)

    companion object {
        private val logger = Logger.getInstance(StateVisitor::class.java)
    }
}

abstract class KtCallExpressionVisitor(holder: ProblemsHolder) : VisitorBase(holder) {

    abstract fun visitCallExpression(callExpression: KtCallExpression)

    override fun visitElement(element: PsiElement) {
        if (element is KtCallExpression)
            visitCallExpression(element)
    }
}

abstract class PathExpressionVisitor(holder: ProblemsHolder) : VisitorBase(holder) {

    abstract fun visitPathExpression(pathExpression: KtExpression)

    override fun visitElement(element: PsiElement) {
        if (element is KtCallExpression)
            element.getInnerPathExpressions().forEach(this::visitPathExpression)

        if (element is KtBinaryExpression)
            element.getInnerPathExpressions().forEach(this::visitPathExpression)
    }
}

abstract class VisitorBase(private val holder: ProblemsHolder) : PsiElementVisitor() {

    protected fun registerGenericErrorOrWarning(
        element: PsiElement,
        message: String,
        vararg localQuickFix: LocalQuickFix,
    ) = registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, *localQuickFix)

    protected fun registerGenericError(
        element: PsiElement,
        message: String,
        vararg localQuickFix: LocalQuickFix,
    ) = registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR, *localQuickFix)

    protected fun registerWarning(
        element: PsiElement,
        message: String,
        vararg localQuickFix: LocalQuickFix,
    ) = registerProblem(element, message, ProblemHighlightType.WARNING, *localQuickFix)

    protected fun registerError(
        element: PsiElement,
        message: String,
        vararg localQuickFix: LocalQuickFix,
    ) = registerProblem(element, message, ProblemHighlightType.ERROR, *localQuickFix)

    protected fun registerWeakWarning(
        element: PsiElement,
        message: String,
        vararg localQuickFix: LocalQuickFix,
    ) = registerProblem(element, message, ProblemHighlightType.WEAK_WARNING, *localQuickFix)

    private fun registerProblem(
        element: PsiElement,
        message: String,
        problemType: ProblemHighlightType,
        vararg localQuickFix: LocalQuickFix,
    ) {
        holder.registerProblem(
            element,
            message,
            problemType,
            ElementManipulators.getValueTextRange(element),
            *localQuickFix
        )
    }
}
