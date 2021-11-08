package com.justai.jaicf.plugin.contexts

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.justai.jaicf.plugin.getBoundedCallExpressionOrNull
import com.justai.jaicf.plugin.scenarios.psi.builders.isStateDeclaration

class StateContext : TemplateContextType("STATE", "State") {
    override fun isInContext(templateActionContext: TemplateActionContext) = templateActionContext.file
        .findElementAt(templateActionContext.startOffset)?.getBoundedCallExpressionOrNull()?.isStateDeclaration == true
}
