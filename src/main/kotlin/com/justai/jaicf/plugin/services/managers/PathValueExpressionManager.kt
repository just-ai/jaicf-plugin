package com.justai.jaicf.plugin.services.managers

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.justai.jaicf.plugin.PATH_ARGUMENT_ANNOTATION_NAME
import com.justai.jaicf.plugin.PLUGIN_PACKAGE
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.pathExpressionsOfBoundedBlock
import com.justai.jaicf.plugin.utils.LiveMap
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.search.allScope
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.idea.search.minus
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class PathValueExpressionManager(project: Project) : Manager(project) {

    private val pathValueService = PathValueMethodsManager(project)

    private val expressionsMap = LiveMap(project) { file ->
        pathValueService.methods
            .flatMap { it.search(file.fileScope()) }
            .map { it.element }
            .flatMap { it.pathExpressionsOfBoundedBlock }
            .map { it.pathExpression }
    }

    fun getExpressions() = expressionsMap.getValues().flatten()

    fun getExpressions(file: KtFile) = expressionsMap[file]

    companion object {
        operator fun get(element: PsiElement): PathValueExpressionManager? =
            if (element.isExist) get(element.project)
            else null

        operator fun get(project: Project): PathValueExpressionManager =
            project.getService(PathValueExpressionManager::class.java)
    }
}

private class PathValueMethodsManager(project: Project) : Manager(project) {

    val methods
        get() = (jaicfMethods + projectMethods.getValues().flatten()).filter { it.isExist }

    private val jaicfMethods by cached(LibraryModificationTracker.getInstance(project)) {
        if (enabled) findUsages(project.allScope() - project.projectScope())
        else emptyList()
    }

    private val projectMethods = LiveMap(project) {
        if (enabled) findUsages(it.fileScope())
        else emptyList()
    }

    private val annotationClass by cachedIfEnabled(LibraryModificationTracker.getInstance(project)) {
        findClass(PLUGIN_PACKAGE, PATH_ARGUMENT_ANNOTATION_NAME, project)
    }

    private fun findUsages(scope: SearchScope): List<KtFunction> {
        return annotationClass?.search(scope)
            ?.mapNotNull { it.element.getParentOfType<KtFunction>(true) }
            ?.distinct() ?: emptyList()
    }
}
