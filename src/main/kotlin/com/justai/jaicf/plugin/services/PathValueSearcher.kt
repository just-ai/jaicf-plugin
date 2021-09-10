package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.justai.jaicf.plugin.PATH_ARGUMENT_ANNOTATION_NAME
import com.justai.jaicf.plugin.PLUGIN_PACKAGE
import com.justai.jaicf.plugin.RecursiveSafeValue
import com.justai.jaicf.plugin.findClass
import org.jetbrains.kotlin.idea.search.allScope
import org.jetbrains.kotlin.idea.search.minus
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class PathValueSearcher(private val project: Project) : Service {

    private val annotationClass = findClass(PLUGIN_PACKAGE, PATH_ARGUMENT_ANNOTATION_NAME, project)
    private val jaicfMethods by lazy { findUsages(project.allScope() - project.projectScope()) }
    private val projectMethods = RecursiveSafeValue(emptyList<KtFunction>()) { findUsages(project.projectScope()) }

    fun getMethodsUsedPathValue(): List<KtFunction> {
        if (VersionService.isAnnotationsUnsupported(project))
            return emptyList()

        if (jaicfMethods.isEmpty())
            logger.error("No annotated method by @$PATH_ARGUMENT_ANNOTATION_NAME was found in jaicf")

        return projectMethods.value + jaicfMethods
    }

    override fun markFileAsModified(file: KtFile) {
        projectMethods.invalid()
    }

    private fun findUsages(scope: SearchScope): List<KtFunction> {
        if (annotationClass == null)
            return emptyList()

        if (DumbService.getInstance(project).isDumb)
            throw ProcessCanceledException()

        return ReferencesSearch.search(annotationClass, scope)
            .toList()
            .mapNotNull { it.element.getParentOfType<KtFunction>(true) }
            .distinct()
    }

    companion object {
        private val logger = Logger.getInstance(this::class.java)

        operator fun get(project: Project): PathValueSearcher =
            ServiceManager.getService(project, PathValueSearcher::class.java)
    }
}
