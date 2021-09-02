package com.justai.jaicf.plugin

import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.services.VersionService
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

val KtCallExpression.innerPathExpressions: List<KtExpression>
    get() =
        if (VersionService.isAnnotationsSupported(project)) {
            val expressions = argumentExpressionsOrDefaultValuesByAnnotation(PATH_ARGUMENT_ANNOTATION_NAME).toMutableList()

            if (hasReceiverAnnotatedBy(PATH_ARGUMENT_ANNOTATION_NAME))
                (this.receiverValue() as? ExpressionReceiver)?.expression?.let { expressions += it }

            expressions.filterNot { it.isNull() }
        } else {
            if (isJumpReaction) listOfNotNull(argumentExpression(REACTIONS_JUMP_PATH_ARGUMENT_NAME))
            else emptyList()
        }


val KtBinaryExpression.innerPathExpressions: List<KtExpression>
    get() {
        return if (VersionService.isAnnotationsSupported(project)) {
            val operation = getChildOfType<KtOperationReferenceExpression>() ?: return emptyList()
            val function = operation.resolveToSource ?: return emptyList()
            val expressions = mutableListOf<KtExpression>()

            if (function.hasReceiverAnnotatedBy(PATH_ARGUMENT_ANNOTATION_NAME))
                (children[0] as? KtExpression)?.let { expressions += it }

            if (function.valueParameters[0].annotationNames.contains(PATH_ARGUMENT_ANNOTATION_NAME))
                (children[2] as? KtExpression)?.let { expressions += it }

            expressions
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
val PsiElement.boundedPathExpression: KtExpression?
    get() {
        val boundedElement = getFirstBoundedElement(
            KtDotQualifiedExpression::class.java,
            KtBinaryExpression::class.java,
            KtValueArgument::class.java
        )

        when (boundedElement) {
            is KtDotQualifiedExpression -> {
                if (!VersionService.isAnnotationsSupported(project))
                    return null

                val callExpression = boundedElement.getChildOfType<KtCallExpression>() ?: return null
                if (callExpression.hasReceiverAnnotatedBy(PATH_ARGUMENT_ANNOTATION_NAME))
                    return (callExpression.receiverValue() as? ExpressionReceiver)?.expression

                return null
            }

            is KtBinaryExpression -> {
                return boundedElement.innerPathExpressions.firstOrNull { this.isInsideOf(listOf(it)) }
                    ?: boundedElement.boundedPathExpression
            }

            is KtValueArgument -> {
                return if (VersionService.isAnnotationsSupported(project)) {
                    // TODO duplicate
                    if (boundedElement.parameter()?.annotationNames?.contains(PATH_ARGUMENT_ANNOTATION_NAME) == true)
                        boundedElement.getArgumentExpression()
                    else null
                } else {
                    if (boundedElement.isJumpReactionArgument)
                        boundedElement.getArgumentExpression()
                    else null
                }
            }

            else -> return null
        }
    }

val PsiElement.pathExpressionsOfBoundedBlock: List<KtExpression>
    get() {
        val boundedElement = getFirstBoundedElement(
            KtDotQualifiedExpression::class.java,
            KtBinaryExpression::class.java,
            KtCallExpression::class.java,
            KtValueArgument::class.java
        )

        return when (boundedElement) {
            is KtDotQualifiedExpression ->
                if (VersionService.isAnnotationsSupported(project))
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

/**
 * @return true if this value argument is inside of `reactions.go` or `reactions.changeState` call expressions.
 * */
private val KtValueArgument.isJumpReactionArgument: Boolean
    get() = getBoundedCallExpressionOrNull()?.isJumpReaction == true && identifier == REACTIONS_JUMP_PATH_ARGUMENT_NAME

private val KtCallExpression.isJumpReaction: Boolean
    get() = isOverride(reactionsClassFqName, REACTIONS_GO_METHOD_NAME) ||
            isOverride(reactionsClassFqName, REACTIONS_CHANGE_STATE_METHOD_NAME)
