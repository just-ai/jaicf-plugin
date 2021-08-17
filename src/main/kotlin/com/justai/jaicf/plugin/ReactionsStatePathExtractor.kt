package com.justai.jaicf.plugin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.debugger.sequence.psi.receiverValue
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.isInsideOf
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver

fun KtCallExpression.getInnerPathExpressions(): List<KtExpression> {
    val expressions: MutableList<KtExpression> =
        argumentExpressionsByAnnotation(PATH_ARGUMENT_ANNOTATION_NAME).toMutableList()
    if (hasReceiverAnnotation(PATH_ARGUMENT_ANNOTATION_NAME))
        (this.receiverValue() as? ExpressionReceiver)?.expression?.let {
            expressions += it
        }

    if (expressions.isNotEmpty())
        return expressions.filter { !it.isNull() }

    return if (isJumpReaction)
        listOfNotNull(argumentExpression(REACTIONS_JUMP_PATH_ARGUMENT_NAME))
    else
        emptyList()
}

fun KtCallExpression.hasReceiverAnnotation(annotationName: String) =
    declaration?.hasReceiverAnnotation(annotationName) ?: false

fun KtCallableDeclaration.hasReceiverAnnotation(annotationName: String) = receiverTypeReference?.annotationEntries
    ?.any { it.shortName?.identifier == annotationName } == true

fun KtBinaryExpression.getInnerPathExpressions(): List<KtExpression> {
    val operation = getChildOfType<KtOperationReferenceExpression>() ?: return emptyList()
    val function = operation.resolve() as? KtNamedFunction ?: return emptyList()
    val expressions = mutableListOf<KtExpression>()

    if (function.hasReceiverAnnotation(PATH_ARGUMENT_ANNOTATION_NAME))
        (children[0] as? KtExpression)?.let { expressions += it }

    if (function.valueParameters[0].annotationNames.contains(PATH_ARGUMENT_ANNOTATION_NAME))
        (children[2] as? KtExpression)?.let { expressions += it }

    return expressions
}

/**
 * @returns [KtExpression] if this PsiElement contains path expression (reactions.go, reactions.buttons(vararg buttonsToState), etc)
 *  or null if no path expression found.
 * */
fun PsiElement.getBoundedPathExpression(): KtExpression? {
    val boundedElement = getFirstBoundedElement(
        KtDotQualifiedExpression::class.java,
        KtBinaryExpression::class.java,
        KtValueArgument::class.java
    )

    when (boundedElement) {
        is KtDotQualifiedExpression -> {
            val callExpression = boundedElement.getChildOfType<KtCallExpression>() ?: return null
            if (callExpression.hasReceiverAnnotation(PATH_ARGUMENT_ANNOTATION_NAME))
                (callExpression.receiverValue() as? ExpressionReceiver)?.expression?.let {
                    return it
                }

            return null
        }

        is KtBinaryExpression -> {
            return boundedElement.getInnerPathExpressions().firstOrNull { this.isInsideOf(listOf(it)) }
                ?: boundedElement.getBoundedPathExpression()
        }

        is KtValueArgument -> {
            return when {
                boundedElement.isJumpReactionArgument -> boundedElement.getArgumentExpression()

                boundedElement.parameter()?.annotationNames?.contains(PATH_ARGUMENT_ANNOTATION_NAME) == true ->
                    boundedElement.getArgumentExpression()

                else -> null
            }
        }
    }

    return null
}

fun PsiElement.getPathExpressionsOfBoundedBlock(): List<KtExpression> {
    val boundedElement = getFirstBoundedElement(
        KtDotQualifiedExpression::class.java,
        KtBinaryExpression::class.java,
        KtCallExpression::class.java,
        KtValueArgument::class.java
    )

    return when (boundedElement) {
        is KtDotQualifiedExpression ->
            boundedElement.getChildOfType<KtCallExpression>()?.let {
                if (it.hasReceiverAnnotation(PATH_ARGUMENT_ANNOTATION_NAME)) it.getInnerPathExpressions()
                else null
            }

        is KtBinaryExpression -> boundedElement.getInnerPathExpressions()

        is KtCallExpression -> boundedElement.getInnerPathExpressions()

        is KtValueArgument -> boundedElement.getBoundedCallExpressionOrNull()?.getInnerPathExpressions()

        else -> null
    } ?: emptyList()
}

/**
 * @return true if this value argument is inside of `reactions.go` or `reactions.changeState` call expressions.
 * */
private val KtValueArgument.isJumpReactionArgument: Boolean
    get() = getBoundedCallExpressionOrNull()?.isJumpReaction == true && identifier == REACTIONS_JUMP_PATH_ARGUMENT_NAME

val KtCallExpression.isJumpReaction: Boolean
    get() = isOverride(reactionsClassFqName, REACTIONS_GO_METHOD_NAME) ||
            isOverride(reactionsClassFqName, REACTIONS_CHANGE_STATE_METHOD_NAME)
