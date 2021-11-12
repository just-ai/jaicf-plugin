package com.justai.jaicf.plugin.utils

import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.scenarios.psi.builders.getAnnotatedStringTemplatesInDeclaration
import com.justai.jaicf.plugin.utils.StatePathExpression.Joined
import com.justai.jaicf.plugin.utils.StatePathExpression.Separated
import org.jetbrains.kotlin.idea.debugger.sequence.psi.receiverValue
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
    get() =
        if (VersionService.getInstance(project).isSupportedJaicfInclude) {
            val expressions =
                argumentExpressionsOrDefaultValuesByAnnotation(PATH_ARGUMENT_ANNOTATION_NAME).toMutableList()

            if (hasReceiverAnnotatedBy(PATH_ARGUMENT_ANNOTATION_NAME))
                (this.receiverValue() as? ExpressionReceiver)?.expression?.let { expressions += it }

            expressions += getAnnotatedStringTemplatesInDeclaration(PATH_ARGUMENT_ANNOTATION_NAME)

            expressions.filterNot { it.isNull() }.map { StatePathExpression.create(this, it) }
        } else {
            emptyList()
        }


val KtBinaryExpression.innerPathExpressions: List<StatePathExpression>
    get() {
        return if (VersionService.getInstance(project).isSupportedJaicfInclude) {
            val function = operationReference.resolveToSource ?: return emptyList()
            val expressions = mutableListOf<KtExpression>()

            if (function.hasReceiverAnnotatedBy(PATH_ARGUMENT_ANNOTATION_NAME))
                left?.let { expressions += it }

            if (function.valueParameters[0].annotationNames.contains(PATH_ARGUMENT_ANNOTATION_NAME))
                right?.let { expressions += it }

            expressions.map { StatePathExpression.create(this, it) }
        } else {
            emptyList()
        }
    }

private fun KtCallExpression.hasReceiverAnnotatedBy(annotationName: String) =
    declaration?.hasReceiverAnnotatedBy(annotationName) ?: false

private fun KtCallableDeclaration.hasReceiverAnnotatedBy(annotationName: String) =
    receiverTypeReference?.annotationEntries
        ?.any { it.shortName?.identifier == annotationName } == true


/**
 * @returns [KtExpression] if this PsiElement contains path expression (reactions.go, reactions.buttons(vararg buttonsToState), etc)
 *  or null if no path expression found.
 * */
val PsiElement.boundedPathExpression: StatePathExpression?
    get() {
        val boundedElement = getFirstBoundedElement(
            listOf(
                KtDotQualifiedExpression::class.java,
                KtBinaryExpression::class.java,
                KtValueArgument::class.java
            )
        )

        when (boundedElement) {
            is KtDotQualifiedExpression -> {
                val callExpression = boundedElement.getChildOfType<KtCallExpression>() ?: return null
                if (!callExpression.hasReceiverAnnotatedBy(PATH_ARGUMENT_ANNOTATION_NAME))
                    return null

                return (callExpression.receiverValue() as? ExpressionReceiver)?.expression?.let {
                    StatePathExpression.create(boundedElement, it)
                }
            }

            is KtBinaryExpression -> {
                return boundedElement.innerPathExpressions.firstOrNull { this.isInsideOf(listOf(it.declaration)) }
                    ?: boundedElement.boundedPathExpression
            }

            is KtValueArgument -> {
                return if (boundedElement.parameter()?.annotationNames?.contains(PATH_ARGUMENT_ANNOTATION_NAME) == true) {
                    val bound = boundedElement.boundedCallExpressionOrNull ?: return null
                    val argumentExpression = boundedElement.getArgumentExpression() ?: return null
                    StatePathExpression.create(bound, argumentExpression)
                } else {
                    null
                }
            }

            else -> {
                return null
            }
        }
    }

val PsiElement.pathExpressionsOfBoundedBlock: List<StatePathExpression>
    get() {
        val boundedElement = getFirstBoundedElement(
            targetTypes = listOf(KtBinaryExpression::class.java, KtCallExpression::class.java),
            allowedTypes = listOf(KtNameReferenceExpression::class.java, KtOperationReferenceExpression::class.java)
        )

        return when (boundedElement) {
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
