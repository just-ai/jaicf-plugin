package com.justai.jaicf.plugin.contexts

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiFile
import com.justai.jaicf.plugin.getBoundedCallExpressionOrNull
import com.justai.jaicf.plugin.isStateDeclaration

class StateContext : TemplateContextType("STATE", "State") {
    override fun isInContext(file: PsiFile, offset: Int) =
        file.findElementAt(offset)?.getBoundedCallExpressionOrNull()?.isStateDeclaration == true
}
