package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.justai.jaicf.plugin.notifications.checkEnvironmentAndNotify
import com.justai.jaicf.plugin.scenarios.psi.ScenarioDataService
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.utils.StatePathExpression
import com.justai.jaicf.plugin.utils.innerPathExpressions
import com.justai.jaicf.plugin.utils.isExist
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

abstract class StateVisitor(holder: ProblemsHolder) : VisitorBase(holder) {

    abstract fun visitState(state: State)

    override fun visitFile(file: PsiFile) {
        if (!checkEnvironmentAndNotify(file))
            return

        if (file !is KtFile)
            return

        val service = ScenarioDataService.getInstance(file) ?: return

        service.getScenarios(file)?.forEach {
            recursiveEntryIntoState(it.innerState)
        }
    }

    private fun recursiveEntryIntoState(state: State) {
        visitState(state)
        state.states.forEach { recursiveEntryIntoState(it) }
    }
}

abstract class KtCallExpressionVisitor(holder: ProblemsHolder) : VisitorBase(holder) {

    abstract fun visitCallExpression(callExpression: KtCallExpression)

    override fun visitElement(element: PsiElement) {
        if (!checkEnvironmentAndNotify(element))
            return

        if (element is KtCallExpression)
            visitCallExpression(element)
    }
}

abstract class PathExpressionVisitor(holder: ProblemsHolder) : VisitorBase(holder) {

    abstract fun visitPathExpression(pathExpression: StatePathExpression)

    override fun visitElement(element: PsiElement) {
        if (!checkEnvironmentAndNotify(element))
            return

        if (element is KtCallExpression)
            element.innerPathExpressions.forEach(this::visitPathExpression)

        if (element is KtBinaryExpression)
            element.innerPathExpressions.forEach(this::visitPathExpression)
    }
}

abstract class AnnotatedElementVisitor(holder: ProblemsHolder) : VisitorBase(holder) {

    abstract fun visitAnnotatedElement(annotatedElement: KtAnnotated)

    override fun visitElement(element: PsiElement) {
        if (!checkEnvironmentAndNotify(element))
            return

        (element as? KtAnnotated)?.let { visitAnnotatedElement(it) }
    }
}

abstract class VisitorBase(private val holder: ProblemsHolder) : PsiElementVisitor() {

    fun checkEnvironmentAndNotify(element: PsiElement): Boolean {
        val project = if (element.isExist) element.project else return false
        return checkEnvironmentAndNotify(project)
    }

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
