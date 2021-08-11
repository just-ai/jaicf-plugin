package com.justai.jaicf.plugin

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import kotlin.math.log

class AppendService(private val project: Project) {

    private val appendsByFiles: LazySafeValue<Map<KtFile, List<TopLevelAppend>>> =
        LazySafeValue(emptyMap())
    private val modifiedFiles: MutableSet<KtFile> = mutableSetOf()
    private val builder: AppendBuilder =
        AppendBuilder(project)

    init {
        appendsByFiles.lazyUpdate {
            this.getProjectAppends().also {
                logger.info("Successful init project appends")
            }
        }
    }

    fun markFileAsModified(file: KtFile) {
        modifiedFiles += file
    }

    fun getAppends(scenario: Scenario): List<Append> {
        updateAppendsIfNeeded()

        return appendsByFiles.value.values
            .flatten()
            .filter { it.scenarioReceiver === scenario }
            .mapNotNull { it.append }
    }

    private fun getProjectAppends() = builder.buildAppends(project.projectScope()).groupBy(TopLevelAppend::file)

    private fun updateAppendsIfNeeded() {
        if (modifiedFiles.isEmpty())
            return

        appendsByFiles.update {
            val appendsMap = it.toMutableMap()

            val files = modifiedFiles.toList()
            modifiedFiles.clear()

            files.forEach { file ->
                val appends = it[file] ?: emptyList()
                val updatedAppends = getUpdatedAppends(file, appends)
                appendsMap[file] = updatedAppends + buildNewAppends(file, updatedAppends)
            }

            appendsMap
        }
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
        existedAppends: List<TopLevelAppend>
    ) = builder.getTopLevelAppendsUsages(file.fileScope())
        .minus(existedAppends.map { it.callExpression })
        .mapNotNull { builder.buildAppend(it) }
        .onEach {
            it.scenarioReceiver.modified()
            it.append?.resolve()?.modified()
        }

    companion object {
        private val logger = Logger.getInstance(this::class.java)
    }
}


class AppendBuilder(val project: Project) {

    private val topLevelMethod: PsiMethod? by lazy { getTopLevelAppendDeclaration() }
    private val scenarioService: ScenarioService = ServiceManager.getService(project, ScenarioService::class.java)

    private fun getTopLevelAppendDeclaration(): PsiMethod? {
        check(!DumbService.getInstance(project).isDumb) { "IDEA in dumb mode" }

        return findClass(
            SCENARIO_PACKAGE,
            SCENARIO_EXTENSIONS_CLASS_NAME,
            project
        )
            ?.allMethods
            ?.first { it.name == APPEND_METHOD_NAME }
            .also {
                if (it == null)
                    logger.info("This project does not contain top level append method")
            }
    }

    fun buildAppends(scope: GlobalSearchScope): List<TopLevelAppend> = getTopLevelAppendsUsages(scope)
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
        topLevelMethod?.let { method ->
            ReferencesSearch.search(method, scope)
                .map { it.element.parent }
                .filterIsInstance<KtCallExpression>()
        } ?: emptyList()

    companion object {
        private val logger = Logger.getInstance(this::class.java)
    }
}
