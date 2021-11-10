package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.justai.jaicf.plugin.utils.PATH_ARGUMENT_ANNOTATION_NAME
import com.justai.jaicf.plugin.utils.stringValueOrNull
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtParameter

class PathValueAnnotationInspection : LocalInspectionTool() {

    override fun getID() = "PathValueAnnotationInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = PathValueVisitor(holder)

    class PathValueVisitor(holder: ProblemsHolder) : AnnotatedElementVisitor(holder) {

        override fun visitAnnotatedElement(annotatedElement: KtAnnotated) {
            if (annotatedElement.isNotAnnotatedWithPathValue)
                return

            when (annotatedElement) {
                is KtParameter ->
                    if (annotatedElement.defaultValue != null && annotatedElement.defaultValue!!.stringValueOrNull == null)
                        registerWeakWarning(annotatedElement.defaultValue!!, message)

                is KtAnnotatedExpression ->
                    if (annotatedElement.baseExpression?.stringValueOrNull == null)
                        registerWeakWarning(annotatedElement.baseExpression ?: annotatedElement, message)
            }
        }

        private val KtAnnotated.isNotAnnotatedWithPathValue
            get() = annotationEntries.none {
                it.shortName?.identifier == PATH_ARGUMENT_ANNOTATION_NAME/* &&
                    it.argumentExpressionOrDefaultValue("shouldUseLiterals")?.text != "false"*/
            }

        companion object {
            private const val message =
                "JAICF Plugin is not able to resolve the path annotated with @PathValue"
        }
    }
}
