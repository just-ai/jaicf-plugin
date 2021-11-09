package com.justai.jaicf.plugin.contexts

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.justai.jaicf.plugin.boundedCallExpressionOrNull
import com.justai.jaicf.plugin.getBoundedLambdaArgumentOrNull
import com.justai.jaicf.plugin.scenarios.psi.builders.isStateDeclaration
import org.jetbrains.kotlin.psi.KtCallExpression

class StateContext : TemplateContextType("STATE", "State") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val probeElement = templateActionContext.file.findElementAt(templateActionContext.startOffset)
        val lambdaArgument = probeElement?.getBoundedLambdaArgumentOrNull(KtCallExpression::class.java) ?: return false
        return lambdaArgument.boundedCallExpressionOrNull?.isStateDeclaration == true
    }
}
