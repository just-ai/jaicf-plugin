package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.justai.jaicf.plugin.notifications.checkEnvironmentAndNotify
import com.justai.jaicf.plugin.core.psi.ScenarioDataService
import com.justai.jaicf.plugin.core.psi.dto.State
import com.justai.jaicf.plugin.core.psi.dto.name
import com.justai.jaicf.plugin.utils.StatePathExpression
import com.justai.jaicf.plugin.utils.VersionService
import com.justai.jaicf.plugin.utils.innerPathExpressions
import com.justai.jaicf.plugin.utils.isExist
import com.justai.jaicf.plugin.utils.isJaicfInclude
import com.justai.jaicf.plugin.utils.measure
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

abstract class StateVisitor(holder: ProblemsHolder) : VisitorBase(holder) {

    abstract fun visitState(state: State)

    override fun visitFile(file: PsiFile) {
        file.project.measure("${this.javaClass.simpleName}.visitFile(${file.name})") {
            if (!checkEnvironmentAndNotify(file))
                return@measure

            if (file !is KtFile)
                return@measure

            val service = ScenarioDataService.getInstance(file) ?: return@measure

            service.getScenarios(file)?.forEach {
                recursiveEntryIntoState(it.innerState)
            }
        }
    }

    private fun recursiveEntryIntoState(state: State) {
        state.project.measure({ "visitState(${state.name})" }) {
            visitState(state)
        }
        state.states.forEach { recursiveEntryIntoState(it) }
    }
}

abstract class KtCallExpressionVisitor(holder: ProblemsHolder) : VisitorBase(holder) {

    abstract fun visitCallExpression(callExpression: KtCallExpression)

    override fun visitElement(element: PsiElement) {
        if (!checkEnvironmentAndNotify(element))
            return

        if (element is KtCallExpression)
            element.measure("${this.javaClass.simpleName}.visitCallExpression(${element.text})") {
                visitCallExpression(element)
            }
    }
}

abstract class PathExpressionVisitor(holder: ProblemsHolder) : VisitorBase(holder) {

    abstract fun visitPathExpression(pathExpression: StatePathExpression)

    override fun visitElement(element: PsiElement) {
        if (!checkEnvironmentAndNotify(element))
            return

        element.measure("${this.javaClass.simpleName}.visitPathExpression(${element.text})") {
            if (element is KtCallExpression)
                element.innerPathExpressions.forEach(this@PathExpressionVisitor::visitPathExpression)

            if (element is KtBinaryExpression)
                element.innerPathExpressions.forEach(this@PathExpressionVisitor::visitPathExpression)
        }
    }
}

abstract class AnnotatedElementVisitor(holder: ProblemsHolder) : VisitorBase(holder) {

    abstract fun visitAnnotatedElement(annotatedElement: KtAnnotated)

    override fun visitElement(element: PsiElement) {
        if (!checkEnvironmentAndNotify(element))
            return

        (element as? KtAnnotated)?.let {
            element.measure("${this.javaClass.simpleName}.visitAnnotatedElement(${element.text})") {
                visitAnnotatedElement(it)
            }
        }
    }
}

abstract class VisitorBase(private val holder: ProblemsHolder) : PsiElementVisitor() {

    fun checkEnvironmentAndNotify(element: PsiElement): Boolean {
        if (VersionService.getInstance(element)?.isJaicfInclude == false) return false
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
