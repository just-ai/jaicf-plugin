package com.justai.jaicf.plugin.scenarios.psi

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.utils.CREATE_MODEL_METHOD_NAME
import com.justai.jaicf.plugin.utils.SCENARIO_EXTENSIONS_CLASS_NAME
import com.justai.jaicf.plugin.utils.SCENARIO_METHOD_NAME
import com.justai.jaicf.plugin.utils.SCENARIO_PACKAGE
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.scenarios.JaicfService
import com.justai.jaicf.plugin.scenarios.psi.builders.buildScenario
import com.justai.jaicf.plugin.trackers.JaicfVersionTracker
import com.justai.jaicf.plugin.utils.LiveMapByFiles
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

class ScenarioDataService(project: Project) : JaicfService(project) {

    private val scenariosBuildMethods by cachedIfEnabled(JaicfVersionTracker.getInstance(project)) {
        findClass(SCENARIO_PACKAGE, SCENARIO_EXTENSIONS_CLASS_NAME, project)
            ?.getMethods(SCENARIO_METHOD_NAME, CREATE_MODEL_METHOD_NAME)
    }

    private val scenariosMap = LiveMapByFiles(project) { file ->
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
        operator fun get(element: PsiElement): ScenarioDataService? =
            if (element.isExist) get(element.project)
            else null

        operator fun get(project: Project): ScenarioDataService =
            ServiceManager.getService(project, ScenarioDataService::class.java)
    }
}
