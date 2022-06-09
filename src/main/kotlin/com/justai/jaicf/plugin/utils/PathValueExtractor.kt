package com.justai.jaicf.plugin.utils

import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.utils.StatePathExpression.Joined
import com.justai.jaicf.plugin.utils.StatePathExpression.Separated
import org.jetbrains.kotlin.idea.debugger.sequence.psi.receiverValue
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.isInsideOf
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver

val KtCallExpression.innerPathExpressions: List<StatePathExpression>
    get() = measure("KtCallExpression.innerPathExpressions") {
        if (VersionService.getInstance(project).isSupportedJaicfInclude) {
            val expressions =
                argumentExpressionsOrDefaultValuesByAnnotation(PATH_ARGUMENT_ANNOTATION_NAME).toMutableList()

            if (hasReceiverAnnotatedBy(PATH_ARGUMENT_ANNOTATION_NAME))
                (receiverValue() as? ExpressionReceiver)?.expression?.let { expressions += it }

            expressions += getAnnotatedExpressionsInDeclaration(PATH_ARGUMENT_ANNOTATION_NAME)

            expressions.filterNot { it.isNull() }.map { StatePathExpression.create(this@innerPathExpressions, it) }
        } else {
            emptyList()
        }
    }


val KtBinaryExpression.innerPathExpressions: List<StatePathExpression>
    get() = measure("KtBinaryExpression.innerPathExpressions") {
        if (VersionService.getInstance(project).isSupportedJaicfInclude) {
            val function = operationReference.resolveToSource ?: return@measure emptyList()
            val expressions = mutableListOf<KtExpression>()

            if (function.hasReceiverAnnotatedBy(PATH_ARGUMENT_ANNOTATION_NAME))
                left?.let { expressions += it }

            if (function.valueParameters[0].annotationNames.contains(PATH_ARGUMENT_ANNOTATION_NAME))
                right?.let { expressions += it }

            expressions.map { StatePathExpression.create(this@innerPathExpressions, it) }
        } else {
            emptyList()
        }
    }

private fun KtCallExpression.hasReceiverAnnotatedBy(annotationName: String) =
    measure("KtCallExpression.hasReceiverAnnotatedBy") {
        declaration?.hasReceiverAnnotatedBy(annotationName) ?: false
    }

private fun KtCallableDeclaration.hasReceiverAnnotatedBy(annotationName: String) =
    measure("KtCallExpression.hasReceiverAnnotatedBy") {
        receiverTypeReference?.annotationEntries
            ?.any { it.shortName?.identifier == annotationName } == true
    }

/**
 * @returns [KtExpression] if this PsiElement contains path expression (reactions.go, reactions.buttons(vararg buttonsToState), etc)
 *  or null if no path expression found.
 * */
val PsiElement.boundedPathExpression: StatePathExpression?
    get() = measure("PsiElement.boundedPathExpression") {
        val boundedElement = getFirstBoundedElement(
            listOf(
                KtDotQualifiedExpression::class.java,
                KtBinaryExpression::class.java,
                KtValueArgument::class.java
            )
        )

        when (boundedElement) {
            is KtDotQualifiedExpression -> {
                val callExpression = boundedElement.getChildOfType<KtCallExpression>() ?: return@measure null
                if (!callExpression.hasReceiverAnnotatedBy(PATH_ARGUMENT_ANNOTATION_NAME))
                    return@measure null

                return@measure (callExpression.receiverValue() as? ExpressionReceiver)?.expression?.let {
                    StatePathExpression.create(boundedElement, it)
                }
            }

            is KtBinaryExpression -> {
                return@measure boundedElement.innerPathExpressions.firstOrNull { isInsideOf(listOf(it.declaration)) }
                    ?: boundedElement.boundedPathExpression
            }

            is KtValueArgument -> {
                return@measure if (boundedElement.parameter()?.annotationNames?.contains(PATH_ARGUMENT_ANNOTATION_NAME) == true) {
                    val bound = boundedElement.boundedCallExpressionOrNull ?: return@measure null
                    val argumentExpression = boundedElement.getArgumentExpression() ?: return@measure null
                    StatePathExpression.create(bound, argumentExpression)
                } else {
                    null
                }
            }

            else -> {
                return@measure null
            }
        }
    }

val PsiElement.pathExpressionsOfBoundedBlock: List<StatePathExpression>
    get() = measure("PsiElement.pathExpressionsOfBoundedBlock") {
        val boundedElement = getFirstBoundedElement(
            targetTypes = listOf(KtBinaryExpression::class.java, KtCallExpression::class.java),
            allowedTypes = listOf(KtNameReferenceExpression::class.java, KtOperationReferenceExpression::class.java)
        )

        when (boundedElement) {
            is KtBinaryExpression -> boundedElement.innerPathExpressions

            is KtCallExpression -> boundedElement.innerPathExpressions

            else -> emptyList()
        }
    }

sealed class StatePathExpression(val usePoint: KtExpression, val declaration: KtExpression) {

    class Joined(usePoint: KtExpression, declaration: KtExpression) :
        StatePathExpression(usePoint, declaration)

    class Separated(usePoint: KtExpression, declaration: KtExpression) :
        StatePathExpression(usePoint, declaration)

    companion object {
        fun create(usePoint: KtExpression, declaration: KtExpression) = when {
            declaration.isInsideOf(listOf(usePoint)) -> Joined(usePoint, declaration)
            else -> Separated(usePoint, declaration)
        }
    }
}

val StatePathExpression.holderExpression: KtExpression
    get() = when (this) {
        is Joined -> declaration
        is Separated -> (usePoint as? KtCallExpression)?.nameReferenceExpression() ?: usePoint
    }

private fun KtCallExpression.getAnnotatedExpressionsInDeclaration(name: String): List<KtExpression> {
    val bodyExpression = this.declaration?.bodyExpression ?: return emptyList()

    val annotatedExpressions = bodyExpression.findChildrenOfType<KtAnnotatedExpression>().filter {
        it.annotationEntries.any { entry -> entry.shortName?.asString() == name }
    }

    return annotatedExpressions.mapNotNull { it.baseExpression }
}
