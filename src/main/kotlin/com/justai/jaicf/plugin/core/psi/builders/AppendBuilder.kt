package com.justai.jaicf.plugin.core.psi.builders

import com.justai.jaicf.plugin.core.psi.dto.NestedAppend
import com.justai.jaicf.plugin.core.psi.dto.State
import com.justai.jaicf.plugin.utils.APPEND_CONTEXT_ARGUMENT_NAME
import com.justai.jaicf.plugin.utils.APPEND_METHOD_NAME
import com.justai.jaicf.plugin.utils.APPEND_OTHER_ARGUMENT_NAME
import com.justai.jaicf.plugin.utils.DO_APPEND_METHOD_NAME
import com.justai.jaicf.plugin.utils.argumentExpression
import com.justai.jaicf.plugin.utils.edgeSelectorExpression
import com.justai.jaicf.plugin.utils.isOverride
import com.justai.jaicf.plugin.utils.isRemoved
import com.justai.jaicf.plugin.utils.rootBuilderClassFqName
import com.justai.jaicf.plugin.utils.scenarioGraphBuilderClassFqName
import com.justai.jaicf.plugin.utils.valueArgument
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression

fun buildAppend(expression: KtCallExpression, state: State? = null): NestedAppend? {
    if (expression.isRemoved || expression.isAppendWithContext || !expression.isAppendWithoutContext)
        return null

    val project = expression.project

    val referenceExpression = when(val argumentExpression = expression.argumentExpression(APPEND_OTHER_ARGUMENT_NAME)) {
        is KtReferenceExpression-> argumentExpression
        is KtDotQualifiedExpression -> argumentExpression.edgeSelectorExpression
        else -> null
    }

    return referenceExpression?.let { NestedAppend(project, it, expression, state) }
}

private val KtCallExpression.isAppendWithoutContext: Boolean
    get() = valueArgument(APPEND_CONTEXT_ARGUMENT_NAME) == null &&
        (isOverride(scenarioGraphBuilderClassFqName, APPEND_METHOD_NAME) ||
            isOverride(rootBuilderClassFqName, APPEND_METHOD_NAME) ||
            isOverride(scenarioGraphBuilderClassFqName, DO_APPEND_METHOD_NAME))

private val KtCallExpression.isAppendWithContext: Boolean
    get() = valueArgument(APPEND_CONTEXT_ARGUMENT_NAME) != null &&
        (isOverride(scenarioGraphBuilderClassFqName, APPEND_METHOD_NAME) ||
            isOverride(rootBuilderClassFqName, APPEND_METHOD_NAME))
