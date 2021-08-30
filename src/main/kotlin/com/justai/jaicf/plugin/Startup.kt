package com.justai.jaicf.plugin

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.justai.jaicf.plugin.services.AppendService
import com.justai.jaicf.plugin.services.ScenarioService
import com.justai.jaicf.plugin.services.PathValueSearcher
import com.justai.jaicf.plugin.services.UsagesSearchService
import org.jetbrains.kotlin.psi.KtFile

class Startup : StartupActivity {
    override fun runActivity(project: Project) {
        DumbService.getInstance(project).runWhenSmart {
            val scenarioService = ServiceManager.getService(project, ScenarioService::class.java)
            val appendService = ServiceManager.getService(project, AppendService::class.java)
            val searchService = ServiceManager.getService(project, PathValueSearcher::class.java)
            val usagesService = ServiceManager.getService(project, UsagesSearchService::class.java)

            PsiManager.getInstance(project)
                .addPsiTreeChangeListener(ScenarioInvalidator(scenarioService, appendService, searchService, usagesService)) { }
        }
    }
}

private class ScenarioInvalidator(
    private val scenarioService: ScenarioService,
    private val appendService: AppendService,
    private val searcher: PathValueSearcher,
    private val usagesService: UsagesSearchService,
) : PsiTreeChangeAdapter() {
    override fun childrenChanged(event: PsiTreeChangeEvent) {
        (event.file as? KtFile)?.let { scenarioService.markFileAsModified(it) }
        (event.file as? KtFile)?.let { appendService.markFileAsModified(it) }
        (event.file as? KtFile)?.let { searcher.markFileAsModified(it) }
        (event.file as? KtFile)?.let { usagesService.markFileAsModified(it) }
    }
}
