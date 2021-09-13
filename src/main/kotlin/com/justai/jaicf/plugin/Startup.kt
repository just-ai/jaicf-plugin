package com.justai.jaicf.plugin

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.justai.jaicf.plugin.services.AppendService
import com.justai.jaicf.plugin.services.ExpressionService
import com.justai.jaicf.plugin.services.JumpMethodsService
import com.justai.jaicf.plugin.services.ScenarioService
import com.justai.jaicf.plugin.services.Service
import com.justai.jaicf.plugin.services.StateUsagesSearchService
import com.justai.jaicf.plugin.services.VersionService
import org.jetbrains.kotlin.psi.KtFile

class Startup : StartupActivity {
    override fun runActivity(project: Project) {
        DumbService.getInstance(project).runWhenSmart {
            val services = listOf(
                ScenarioService::class.java,
                AppendService::class.java,
                JumpMethodsService::class.java,
                StateUsagesSearchService::class.java,
                VersionService::class.java,
                ExpressionService::class.java
            ).map { ServiceManager.getService(project, it) }

            PsiManager.getInstance(project).addPsiTreeChangeListener(ScenarioInvalidator(services)) { }
        }
    }
}

private class ScenarioInvalidator(
    val services: List<Service>,
) : PsiTreeChangeAdapter() {
    override fun childrenChanged(event: PsiTreeChangeEvent) {
        (event.file as? KtFile)?.let { file ->
            services.forEach { it.markFileAsModified(file) }
        }
    }
}
