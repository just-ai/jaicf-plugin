package com.justai.jaicf.plugin.services.managers

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
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
import org.jetbrains.kotlin.utils.addToStdlib.measureTimeMillisWithResult

class TopLevelAppendDataManager(project: Project) : Manager(project) {

    fun getAppends(file: KtFile) =
        if (enabled) appendsMap[file]
        else null

    fun getAppends(): List<TopLevelAppend> =
        if (enabled) appendsMap.getValues().flatten()
    else emptyList()


    private val appendsMap = LiveMap(project) { file ->
        val start = System.currentTimeMillis()
        val (l, version) = measureTimeMillisWithResult {
            getTopLevelAppendsUsages(file.fileScope()).mapNotNull { buildTopLevelAppend(it) }
        }
        val end = System.currentTimeMillis()

        println("TopLevelAppendDataManager: appendsMap updated [${file.name}]: $l ($start-$end)")
        version
    }

    private val appendMethod by cachedIfEnabled(LibraryModificationTracker.getInstance(project)) {
        val (l, version) = measureTimeMillisWithResult {
            findClass(SCENARIO_PACKAGE, SCENARIO_EXTENSIONS_CLASS_NAME, project)
                ?.allMethods
                ?.first { it.name == APPEND_METHOD_NAME }
        }

        println("TopLevelAppendDataManager: appendMethod updated: $l")
        version
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
            project.getService(TopLevelAppendDataManager::class.java)
    }
}
