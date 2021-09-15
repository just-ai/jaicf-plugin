package com.justai.jaicf.plugin.services.managers.builders

import com.justai.jaicf.plugin.APPEND_CONTEXT_ARGUMENT_NAME
import com.justai.jaicf.plugin.APPEND_METHOD_NAME
import com.justai.jaicf.plugin.APPEND_OTHER_ARGUMENT_NAME
import com.justai.jaicf.plugin.DO_APPEND_METHOD_NAME
import com.justai.jaicf.plugin.argumentExpression
import com.justai.jaicf.plugin.isOverride
import com.justai.jaicf.plugin.isRemoved
import com.justai.jaicf.plugin.rootBuilderClassFqName
import com.justai.jaicf.plugin.scenarioGraphBuilderClassFqName
import com.justai.jaicf.plugin.services.managers.dto.Append
import com.justai.jaicf.plugin.services.managers.dto.State
import com.justai.jaicf.plugin.valueArgument
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression

fun buildAppend(expression: KtCallExpression, state: State? = null): Append? {
    if (expression.isRemoved || expression.isAppendWithContext || !expression.isAppendWithoutContext)
        return null

    val project = expression.project
    val argumentExpression =
        expression.argumentExpression(APPEND_OTHER_ARGUMENT_NAME) as? KtReferenceExpression
    return argumentExpression?.let { Append(project, it, expression, state) }
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
