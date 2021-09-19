package com.justai.jaicf.plugin.services.managers

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.CREATE_MODEL_METHOD_NAME
import com.justai.jaicf.plugin.SCENARIO_EXTENSIONS_CLASS_NAME
import com.justai.jaicf.plugin.SCENARIO_METHOD_NAME
import com.justai.jaicf.plugin.SCENARIO_PACKAGE
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.services.managers.builders.buildScenario
import com.justai.jaicf.plugin.utils.LiveMap
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

class ScenarioDataManager(project: Project) : Manager(project) {

    private val scenariosBuildMethods by cachedIfEnabled(LibraryModificationTracker.getInstance(project)) {
        findClass(SCENARIO_PACKAGE, SCENARIO_EXTENSIONS_CLASS_NAME, project)
            ?.getMethods(SCENARIO_METHOD_NAME, CREATE_MODEL_METHOD_NAME)
    }

    private val scenariosMap = LiveMap(project) { file ->
        scenariosBuildMethods
            ?.flatMap { it.search(file.fileScope()) }
            ?.map { it.element.parent }
            ?.filterIsInstance<KtCallExpression>()
            ?.mapNotNull { buildScenario(it) }
            ?: emptyList()
    }

    fun getScenarios(file: KtFile) =
        if (enabled) (file.originalFile as? KtFile)?.let { scenariosMap[it] }
        else null

    fun getScenarios() =
        if (enabled) scenariosMap.getValues().flatten()
        else emptyList()

    companion object {
        operator fun get(element: PsiElement): ScenarioDataManager? =
            if (element.isExist) get(element.project)
            else null

        operator fun get(project: Project): ScenarioDataManager =
            project.getService(ScenarioDataManager::class.java)
    }
}
