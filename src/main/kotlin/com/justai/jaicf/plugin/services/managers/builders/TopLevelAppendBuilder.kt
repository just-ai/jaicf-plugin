package com.justai.jaicf.plugin.services.managers.builders

import com.justai.jaicf.plugin.services.managers.dto.TopLevelAppend
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

fun buildTopLevelAppend(callExpression: KtCallExpression): TopLevelAppend? {
    val dotExpression = callExpression.parent as? KtDotQualifiedExpression ?: return null

    return TopLevelAppend(callExpression.project, callExpression, dotExpression)
}