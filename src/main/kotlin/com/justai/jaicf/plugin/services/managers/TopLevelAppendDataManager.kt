package com.justai.jaicf.plugin.services.managers

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.justai.jaicf.plugin.APPEND_METHOD_NAME
import com.justai.jaicf.plugin.SCENARIO_EXTENSIONS_CLASS_NAME
import com.justai.jaicf.plugin.SCENARIO_PACKAGE
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.services.managers.builders.buildTopLevelAppend
import com.justai.jaicf.plugin.services.managers.dto.TopLevelAppend
import com.justai.jaicf.plugin.utils.LiveMap
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

class TopLevelAppendDataManager(project: Project) : Manager(project) {

    fun getAppends(file: KtFile) =
        if (enabled) appendsMap[file]
        else null

    fun getAppends(): List<TopLevelAppend> =
        if (enabled) appendsMap.getValues().flatten()
        else emptyList()


    private val appendsMap = LiveMap(project) { file ->
        getTopLevelAppendsUsages(file.fileScope()).mapNotNull { buildTopLevelAppend(it) }
    }

    private val appendMethod by cachedIfEnabled(LibraryModificationTracker.getInstance(project)) {
        findClass(SCENARIO_PACKAGE, SCENARIO_EXTENSIONS_CLASS_NAME, project)
            ?.allMethods
            ?.first { it.name == APPEND_METHOD_NAME }
    }

    private fun getTopLevelAppendsUsages(scope: GlobalSearchScope): List<KtCallExpression> =
        appendMethod?.search(scope)
            ?.map { it.element.parent }
            ?.filterIsInstance<KtCallExpression>()
            ?: emptyList()

    companion object {
        operator fun get(element: PsiElement): TopLevelAppendDataManager? =
            if (element.isExist) get(element.project)
            else null

        operator fun get(project: Project): TopLevelAppendDataManager =
            ServiceManager.getService(project, TopLevelAppendDataManager::class.java)
    }
}
