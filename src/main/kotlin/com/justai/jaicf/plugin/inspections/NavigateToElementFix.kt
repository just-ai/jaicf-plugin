package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.justai.jaicf.plugin.scenarios.psi.dto.State

class NavigateToState(message: String, state: State) : NavigateToElementFix(
    message,
    state.stateExpression
)

open class NavigateToElementFix(private val message: String, arg: PsiElement) : LocalQuickFix {

    private val pointer = SmartPointerManager.getInstance(arg.project).createSmartPsiElementPointer(arg)

    override fun getFamilyName() = message

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = pointer.element ?: return
        val file = element.containingFile ?: return
        val offset = element.textRange.startOffset
        PsiNavigationSupport.getInstance().createNavigatable(project, file.virtualFile, offset).navigate(true)
    }
}
