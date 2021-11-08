package com.justai.jaicf.plugin.scenarios.psi.builders

import com.justai.jaicf.plugin.utils.APPEND_CONTEXT_ARGUMENT_NAME
import com.justai.jaicf.plugin.utils.APPEND_METHOD_NAME
import com.justai.jaicf.plugin.utils.APPEND_OTHER_ARGUMENT_NAME
import com.justai.jaicf.plugin.utils.DO_APPEND_METHOD_NAME
import com.justai.jaicf.plugin.argumentExpression
import com.justai.jaicf.plugin.isOverride
import com.justai.jaicf.plugin.isRemoved
import com.justai.jaicf.plugin.utils.rootBuilderClassFqName
import com.justai.jaicf.plugin.utils.scenarioGraphBuilderClassFqName
import com.justai.jaicf.plugin.scenarios.psi.dto.Append
import com.justai.jaicf.plugin.scenarios.psi.dto.State
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
