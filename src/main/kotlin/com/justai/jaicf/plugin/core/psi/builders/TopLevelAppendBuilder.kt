package com.justai.jaicf.plugin.core.psi.builders

import com.justai.jaicf.plugin.core.psi.dto.TopLevelAppend
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

fun buildTopLevelAppend(callExpression: KtCallExpression): TopLevelAppend? {
    val dotExpression = callExpression.parent as? KtDotQualifiedExpression ?: return null

    return TopLevelAppend(callExpression.project, callExpression, dotExpression)
}
