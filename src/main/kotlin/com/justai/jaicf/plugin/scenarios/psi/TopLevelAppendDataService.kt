package com.justai.jaicf.plugin.scenarios.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.justai.jaicf.plugin.scenarios.JaicfService
import com.justai.jaicf.plugin.scenarios.psi.builders.buildTopLevelAppend
import com.justai.jaicf.plugin.trackers.JaicfVersionTracker
import com.justai.jaicf.plugin.utils.APPEND_METHOD_NAME
import com.justai.jaicf.plugin.utils.LiveMapByFiles
import com.justai.jaicf.plugin.utils.SCENARIO_EXTENSIONS_CLASS_NAME
import com.justai.jaicf.plugin.utils.SCENARIO_PACKAGE
import com.justai.jaicf.plugin.utils.isExist
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.psi.KtCallExpression

class TopLevelAppendDataService(project: Project) : JaicfService(project) {

    private val appendMethod by cachedIfEnabled(JaicfVersionTracker.getInstance(project)) {
        findClass(SCENARIO_PACKAGE, SCENARIO_EXTENSIONS_CLASS_NAME, project)
            ?.allMethods
            ?.first { it.name == APPEND_METHOD_NAME }
    }

    private val appendsMap = LiveMapByFiles(project) { file ->
        getTopLevelAppendsUsages(file.fileScope()).mapNotNull { buildTopLevelAppend(it) }
    }

    fun getAppends() =
        if (enabled) appendsMap.getValues().flatten()
        else emptyList()

    private fun getTopLevelAppendsUsages(scope: GlobalSearchScope): List<KtCallExpression> =
        appendMethod?.search(scope)
            ?.map { it.element.parent }
            ?.filterIsInstance<KtCallExpression>()
            ?: emptyList()

    companion object {
        fun getInstance(element: PsiElement): TopLevelAppendDataService? =
            if (element.isExist) getInstance(element.project)
            else null

        fun getInstance(project: Project): TopLevelAppendDataService =
            project.getService(TopLevelAppendDataService::class.java)
    }
}
