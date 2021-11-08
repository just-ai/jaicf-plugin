package com.justai.jaicf.plugin

import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.StatePathExpression.BoundedExpression
import com.justai.jaicf.plugin.StatePathExpression.OutBoundedExpression
import com.justai.jaicf.plugin.utils.VersionService
import com.justai.jaicf.plugin.utils.isJaicfSupported
import com.justai.jaicf.plugin.scenarios.psi.builders.getAnnotatedStringTemplatesInDeclaration
import com.justai.jaicf.plugin.utils.PATH_ARGUMENT_ANNOTATION_NAME
import org.jetbrains.kotlin.idea.debugger.sequence.psi.receiverValue
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.isInsideOf
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver

val KtCallExpression.innerPathExpressions: List<StatePathExpression>
    get() =
        if (VersionService.getInstance(project).isJaicfSupported) {
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
        return if (VersionService.getInstance(project).isJaicfSupported) {
            val operation = getChildOfType<KtOperationReferenceExpression>() ?: return emptyList()
            val function = operation.resolveToSource ?: return emptyList()
            val expressions = mutableListOf<KtExpression>()

            if (function.hasReceiverAnnotatedBy(PATH_ARGUMENT_ANNOTATION_NAME))
                (children[0] as? KtExpression)?.let { expressions += it }

            if (function.valueParameters[0].annotationNames.contains(PATH_ARGUMENT_ANNOTATION_NAME))
                (children[2] as? KtExpression)?.let { expressions += it }

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
            KtDotQualifiedExpression::class.java,
            KtBinaryExpression::class.java,
            KtValueArgument::class.java
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
                return boundedElement.innerPathExpressions.firstOrNull { this.isInsideOf(listOf(it.pathExpression)) }
                    ?: boundedElement.boundedPathExpression
            }

            is KtValueArgument -> {
                return if (VersionService.getInstance(project).isJaicfSupported) {
                    if (boundedElement.parameter()?.annotationNames?.contains(PATH_ARGUMENT_ANNOTATION_NAME) == true) {
                        val bound = boundedElement.getBoundedCallExpressionOrNull() ?: return null
                        val argumentExpression = boundedElement.getArgumentExpression() ?: return null
                        StatePathExpression.create(bound, argumentExpression)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

            else -> return null
        }
    }

val PsiElement.pathExpressionsOfBoundedBlock: List<StatePathExpression>
    get() {
        val boundedElement = getFirstBoundedElement(
            KtDotQualifiedExpression::class.java,
            KtBinaryExpression::class.java,
            KtCallExpression::class.java,
            KtValueArgument::class.java
        )

        return when (boundedElement) {
            is KtDotQualifiedExpression ->
                if (VersionService.getInstance(project).isJaicfSupported)
                    boundedElement.getChildOfType<KtCallExpression>()?.let {
                        if (it.hasReceiverAnnotatedBy(PATH_ARGUMENT_ANNOTATION_NAME)) it.innerPathExpressions
                        else null
                    }
                else
                    null

            is KtBinaryExpression -> boundedElement.innerPathExpressions

            is KtCallExpression -> boundedElement.innerPathExpressions

            is KtValueArgument -> boundedElement.getBoundedCallExpressionOrNull()?.innerPathExpressions

            else -> null
        } ?: emptyList()
    }

sealed class StatePathExpression(val bound: KtExpression, val pathExpression: KtExpression) {
    class BoundedExpression(bound: KtExpression, pathExpression: KtExpression) :
        StatePathExpression(bound, pathExpression)

    class OutBoundedExpression(bound: KtExpression, pathExpression: KtExpression) :
        StatePathExpression(bound, pathExpression)

    companion object {
        fun create(bound: KtExpression, pathExpression: KtExpression) = when {
            pathExpression.isInsideOf(listOf(bound)) -> BoundedExpression(bound, pathExpression)
            else -> OutBoundedExpression(bound, pathExpression)
        }
    }
}

val StatePathExpression.holderExpression: KtExpression
    get() = when (this) {
        is BoundedExpression -> pathExpression
        is OutBoundedExpression -> (bound as? KtCallExpression)?.nameReferenceExpression() ?: bound
    }
