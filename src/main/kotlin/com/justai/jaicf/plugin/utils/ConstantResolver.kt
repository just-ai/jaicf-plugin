package com.justai.jaicf.plugin.utils

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.idea.inspections.AbstractPrimitiveRangeToInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.psi.KtExpression

class ConstantResolver(project: Project) {

    private val resolvedExpressions by project.cached(PsiModificationTracker.MODIFICATION_COUNT) {
        mutableMapOf<KtExpression, String?>()
    }

    fun resolveExpression(expression: KtExpression) =
        resolvedExpressions.let {
            if (it.contains(expression)) it[expression]
            else resolve(expression).also { res -> resolvedExpressions[expression] = res }
        }

    private fun resolve(expression: KtExpression) = expression.constantValueOrNull()?.value?.toString()

    companion object {
        fun getInstance(element: PsiElement): ConstantResolver? =
            if (element.isExist) getInstance(element.project)
            else null

        fun getInstance(project: Project): ConstantResolver =
            ServiceManager.getService(project, ConstantResolver::class.java)
    }
}
