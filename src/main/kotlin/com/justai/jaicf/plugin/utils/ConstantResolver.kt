package com.justai.jaicf.plugin.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.idea.inspections.AbstractPrimitiveRangeToInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.isPlain
import org.jetbrains.kotlin.psi.psiUtil.plainContent

class ConstantResolver(project: Project) {

    private val resolvedExpressions by project.cached(PsiModificationTracker.MODIFICATION_COUNT) {
        mutableMapOf<KtExpression, String?>()
    }

    fun resolveExpression(expression: KtExpression) =
        resolvedExpressions.let {
            if (it.contains(expression)) it[expression]
            else resolve(expression).also { res -> resolvedExpressions[expression] = res }
        }

    private fun resolve(element: PsiElement): String? {
        return when {
            element.isRemoved ->
                null

            element is KtBinaryExpression && element.isStringConcatenationExpression ->
                element.operands?.fold("" as String?) { acc, operand ->
                    resolve(operand)?.let { acc?.plus(it) }
                } ?: element.constantValueOrNull()?.value?.toString()

            element is KtStringTemplateExpression && element.isPlain() ->
                element.plainContent

            element is KtStringTemplateExpression && element.hasInterpolation() ->
                element.children.fold("" as String?) { acc, operand ->
                    resolve(operand)?.let { acc?.plus(it) }
                } ?: element.constantValueOrNull()?.value?.toString()

            element is KtLiteralStringTemplateEntry ->
                element.text

            element is KtSimpleNameStringTemplateEntry ->
                element.expression?.let { resolve(it) }

            element is KtBlockStringTemplateEntry ->
                element.expression?.let { resolve(it) }

            element is KtNameReferenceExpression ->
                (element.resolve() as? KtProperty)?.initializer?.let { resolve(it) }
                    ?: element.constantValueOrNull()?.value?.toString()

            element is KtExpression ->
                element.constantValueOrNull()?.value?.toString()

            else ->
                null
        }
    }

    companion object {
        fun getInstance(element: PsiElement): ConstantResolver? =
            if (element.isExist) getInstance(element.project)
            else null

        fun getInstance(project: Project): ConstantResolver =
            project.getService(ConstantResolver::class.java)
    }
}
