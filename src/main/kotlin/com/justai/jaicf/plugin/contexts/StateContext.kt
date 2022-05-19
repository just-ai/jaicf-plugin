package com.justai.jaicf.plugin.contexts

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.justai.jaicf.plugin.scenarios.psi.builders.isStateDeclaration
import com.justai.jaicf.plugin.utils.VersionService
import com.justai.jaicf.plugin.utils.boundedCallExpressionOrNull
import com.justai.jaicf.plugin.utils.getBoundedLambdaArgumentOrNull
import com.justai.jaicf.plugin.utils.isJaicfInclude
import com.justai.jaicf.plugin.utils.measure
import org.jetbrains.kotlin.psi.KtCallExpression

class StateContext : TemplateContextType("STATE", "State") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean =
        templateActionContext.file.project.measure("StateContext.isInContext") {
            if (!VersionService.getInstance(templateActionContext.file.project).isJaicfInclude) return@measure false

            val probeElement = templateActionContext.file.findElementAt(templateActionContext.startOffset)
            val boundedLambda =
                probeElement?.getBoundedLambdaArgumentOrNull(KtCallExpression::class.java) ?: return@measure false
            return@measure boundedLambda.boundedCallExpressionOrNull?.isStateDeclaration == true
        }
}
