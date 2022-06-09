package com.justai.jaicf.plugin.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.core.JaicfService
import com.justai.jaicf.plugin.core.psi.ScenarioDataService.Companion.getInstance
import com.justai.jaicf.plugin.core.psi.builders.buildScenario
import com.justai.jaicf.plugin.core.psi.dto.Scenario
import com.justai.jaicf.plugin.trackers.JaicfVersionTracker
import com.justai.jaicf.plugin.utils.CREATE_MODEL_METHOD_NAME
import com.justai.jaicf.plugin.utils.LiveMapByFiles
import com.justai.jaicf.plugin.utils.SCENARIO_EXTENSIONS_CLASS_NAME
import com.justai.jaicf.plugin.utils.SCENARIO_METHOD_NAME
import com.justai.jaicf.plugin.utils.SCENARIO_PACKAGE
import com.justai.jaicf.plugin.utils.isExist
import com.justai.jaicf.plugin.utils.measure
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

class ScenarioDataService(project: Project) : JaicfService(project) {

    private val scenariosBuildMethods by cachedIfEnabled(JaicfVersionTracker.getInstance(project)) {
        findClass(SCENARIO_PACKAGE, SCENARIO_EXTENSIONS_CLASS_NAME, project)
            ?.getMethods(SCENARIO_METHOD_NAME, CREATE_MODEL_METHOD_NAME)
    }

    // TODO Optimize Использовать трекер psiReference в каждом файле
    private val scenariosMap = LiveMapByFiles(project) { file ->
        measure("Update scenario in ${file.name}") {
            scenariosBuildMethods
                ?.flatMap { it.search(file.fileScope()) }
                ?.map { it.element.parent }
                ?.filterIsInstance<KtCallExpression>()
                ?.mapNotNull { buildScenario(it) }
                ?: emptyList()
        }
    }

    fun getScenarios(file: KtFile): List<Scenario>? = measure("ScenarioDataService.getScenarios(${file.name})") {
        if (enabled) (file.originalFile as? KtFile)?.let { scenariosMap[it] }
        else null
    }

    fun getScenarios(): List<Scenario> = measure("ScenarioDataService.getScenarios()") {
        if (enabled) scenariosMap.getNotNullValues().flatten()
        else emptyList()
    }

    companion object {
        fun getInstance(element: PsiElement): ScenarioDataService? =
            if (element.isExist) getInstance(element.project)
            else null

        fun getInstance(project: Project): ScenarioDataService =
            project.getService(ScenarioDataService::class.java)
    }
}

val Project.scenarios get() = getInstance(this).getScenarios()
