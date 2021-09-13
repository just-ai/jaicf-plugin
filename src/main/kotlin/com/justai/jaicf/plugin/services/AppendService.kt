package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.justai.jaicf.plugin.APPEND_METHOD_NAME
import com.justai.jaicf.plugin.RecursiveSafeValue
import com.justai.jaicf.plugin.SCENARIO_EXTENSIONS_CLASS_NAME
import com.justai.jaicf.plugin.SCENARIO_PACKAGE
import com.justai.jaicf.plugin.Scenario
import com.justai.jaicf.plugin.TopLevelAppend
import com.justai.jaicf.plugin.findClass
import com.justai.jaicf.plugin.getRootDotReceiver
import com.justai.jaicf.plugin.isActual
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.isRemoved
import com.justai.jaicf.plugin.isValid
import com.justai.jaicf.plugin.resolve
import com.justai.jaicf.plugin.search
import com.justai.jaicf.plugin.services.ScenarioService.Companion
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile

class AppendService(private val project: Project) : Service {

    private val builder = AppendBuilder(project)
    private var modifiedFiles = setOf<KtFile>()
    private val appendsByFiles by lazy {
        RecursiveSafeValue(getProjectAppends(), updater = this::updateAppendsIfNeeded)
    }

    override fun markFileAsModified(file: KtFile) {
        if (file !in modifiedFiles)
            modifiedFiles = modifiedFiles + file

        appendsByFiles.invalid()
    }

    fun getAppends(scenario: Scenario) = appendsByFiles.value.values
        .flatten()
        .filter { it.scenarioReceiver === scenario }
        .mapNotNull { it.append }

    private fun getProjectAppends() = builder.buildAppends(project.projectScope()).groupBy(TopLevelAppend::file)

    private fun updateAppendsIfNeeded(map: Map<KtFile, List<TopLevelAppend>>): Map<KtFile, List<TopLevelAppend>> {
        if (modifiedFiles.isEmpty())
            return map

        val appendsMap = map.toMutableMap()

        val files = modifiedFiles.toList()
        modifiedFiles = emptySet()

        files.forEach { file ->
            val appends = map[file] ?: emptyList()
            val updatedAppends = getUpdatedAppends(file, appends)
            appendsMap[file] = updatedAppends + buildNewAppends(file, updatedAppends)
        }

        return appendsMap
    }

    private fun getUpdatedAppends(file: KtFile, appends: List<TopLevelAppend>) =
        appends.mapNotNull {
            if (it.isRemoved) {
                logger.info("Removed an append in ${file.name}")
                return@mapNotNull null
            }

            if (it.isActual && it.scenarioReceiver.isValid)
                return@mapNotNull it

            return@mapNotNull builder
                .buildAppend(it.callExpression)
                ?.also { append ->
                    append.scenarioReceiver.modified()
                    append.append?.resolve()?.modified()
                }
        }

    private fun buildNewAppends(
        file: KtFile,
        existedAppends: List<TopLevelAppend>,
    ) = builder.getTopLevelAppendsUsages(file.fileScope())
        .minus(existedAppends.map { it.callExpression })
        .mapNotNull { builder.buildAppend(it) }
        .onEach {
            it.scenarioReceiver.modified()
            it.append?.resolve()?.modified()
        }

    companion object {
        private val logger = Logger.getInstance(this::class.java)

        operator fun get(element: PsiElement): AppendService? =
            if (element.isExist) ServiceManager.getService(element.project, AppendService::class.java)
            else null

        operator fun get(project: Project): AppendService =
            ServiceManager.getService(project, AppendService::class.java)
    }
}


class AppendBuilder(val project: Project) {

    private val topLevelMethod: PsiMethod? by lazy { getTopLevelAppendDeclaration() }
    private val scenarioService = ScenarioService[project]

    private fun getTopLevelAppendDeclaration(): PsiMethod? {
        return findClass(SCENARIO_PACKAGE, SCENARIO_EXTENSIONS_CLASS_NAME, project)
            ?.allMethods
            ?.first { it.name == APPEND_METHOD_NAME }
            .also {
                if (it == null)
                    logger.info("This project does not contain top level append method")
            }
    }

    fun buildAppends(scope: GlobalSearchScope) = getTopLevelAppendsUsages(scope)
        .mapNotNull { buildAppend(it) }

    fun buildAppend(callExpression: KtCallExpression): TopLevelAppend? {
        val dotExpression = callExpression.parent as? KtDotQualifiedExpression ?: return null
        val receiverExpression = dotExpression.getRootDotReceiver()

        if (receiverExpression == null) {
            logger.warn("Top level append has no dot receiver. $dotExpression")
            return null
        }

        val receiverScenario = scenarioService.resolveScenario(receiverExpression)

        if (receiverScenario == null) {
            logger.warn("Cannot resolve scenario of $receiverExpression")
            return null
        }

        return if (receiverScenario.isValid) {
            TopLevelAppend(receiverScenario, callExpression, dotExpression)
        } else {
            logger.warn("Receiver scenario of top level append is invalid. $receiverScenario")
            null
        }
    }

    fun getTopLevelAppendsUsages(scope: GlobalSearchScope): List<KtCallExpression> =
        topLevelMethod?.search(scope)
            ?.map { it.element.parent }
            ?.filterIsInstance<KtCallExpression>()
            ?: emptyList()

    companion object {
        private val logger = Logger.getInstance(this::class.java)
    }
}
