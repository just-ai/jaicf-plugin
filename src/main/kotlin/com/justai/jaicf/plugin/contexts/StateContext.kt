package com.justai.jaicf.plugin.contexts

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.justai.jaicf.plugin.scenarios.psi.builders.isStateDeclaration
import com.justai.jaicf.plugin.utils.boundedCallExpressionOrNull
import com.justai.jaicf.plugin.utils.getBoundedLambdaArgumentOrNull
import org.jetbrains.kotlin.psi.KtCallExpression

class StateContext : TemplateContextType("STATE", "State") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val probeElement = templateActionContext.file.findElementAt(templateActionContext.startOffset)
        val boundedLambda = probeElement?.getBoundedLambdaArgumentOrNull(KtCallExpression::class.java) ?: return false
        return boundedLambda.boundedCallExpressionOrNull?.isStateDeclaration == true
    }
}
