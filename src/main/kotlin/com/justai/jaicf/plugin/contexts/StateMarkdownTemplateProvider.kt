package com.justai.jaicf.plugin.contexts

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

class StateMarkdownTemplateProvider : DefaultLiveTemplatesProvider {
    override fun getDefaultLiveTemplateFiles(): Array<String> = arrayOf("liveTemplates/state")

    override fun getHiddenLiveTemplateFiles(): Array<String>? = null
}

