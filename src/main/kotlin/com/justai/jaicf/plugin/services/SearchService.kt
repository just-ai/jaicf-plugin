package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.justai.jaicf.plugin.LazySafeValue
import com.justai.jaicf.plugin.PATH_ARGUMENT_ANNOTATION_NAME
import com.justai.jaicf.plugin.PLUGIN_PACKAGE
import com.justai.jaicf.plugin.findClass
import org.jetbrains.kotlin.idea.search.allScope
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class SearchService(private val project: Project) {

    private val annotationClass = findClass(PLUGIN_PACKAGE, PATH_ARGUMENT_ANNOTATION_NAME, project)
    private val jaicfMethods = LazySafeValue(emptyList<KtFunction>()) { findUsages(project.allScope()) }
    private val projectMethods = LazySafeValue(emptyList<KtFunction>()) { findUsages(project.projectScope()) }

    fun getMethodsUsedPathValue(): List<KtFunction> {
        if (annotationClass == null)
            return emptyList()

        val annotatedMethods = projectMethods.value + jaicfMethods.value

        if (annotatedMethods.isEmpty()) {
            logger.info("No annotated method by @$PATH_ARGUMENT_ANNOTATION_NAME was found")
        }

        return annotatedMethods
    }

    fun markFileAsModified(file: KtFile) {
        projectMethods.lazyUpdate { findUsages(project.projectScope()) }
    }

    private fun findUsages(scope: SearchScope): List<KtFunction> {
        if (annotationClass == null)
            return emptyList()

        return ReferencesSearch.search(annotationClass, scope)
            .toList()
            .mapNotNull { it.element.getParentOfType<KtFunction>(true) }
            .distinct()
    }

    companion object {
        private val logger = Logger.getInstance(this::class.java)

        fun get(project: Project): SearchService = ServiceManager.getService(project, SearchService::class.java)
    }
}
