package com.justai.jaicf.plugin.scenarios.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.justai.jaicf.plugin.utils.isExist
import com.justai.jaicf.plugin.pathExpressionsOfBoundedBlock
import com.justai.jaicf.plugin.scenarios.JaicfService
import com.justai.jaicf.plugin.trackers.JaicfVersionTracker
import com.justai.jaicf.plugin.utils.LiveMapByFiles
import com.justai.jaicf.plugin.utils.PATH_ARGUMENT_ANNOTATION_NAME
import com.justai.jaicf.plugin.utils.PLUGIN_PACKAGE
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.search.allScope
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.idea.search.minus
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class PathValueExpressionsService(project: Project) : JaicfService(project) {

    private val pathValueService = PathValueMethodsService(project)

    private val expressionsMap = LiveMapByFiles(project) { file ->
        pathValueService.methods
            .flatMap { it.search(file.fileScope()) }
            .map { it.element }
            .flatMap { it.pathExpressionsOfBoundedBlock }
            .map { it.pathExpression }
    }

    fun getExpressions() = expressionsMap.getValues().flatten()

    companion object {
        fun getInstance(element: PsiElement): PathValueExpressionsService? =
            if (element.isExist) getInstance(element.project)
            else null

        fun getInstance(project: Project): PathValueExpressionsService =
            project.getService(PathValueExpressionsService::class.java)
    }
}

class PathValueMethodsService(project: Project) : JaicfService(project) {

    val methods
        get() = (jaicfMethods + projectMethods.getValues().flatten()).filter { it.isExist }

    val jaicfMethods by cached(LibraryModificationTracker.getInstance(project)) {
        if (enabled) findUsages(project.allScope() - project.projectScope())
        else emptyList()
    }

    private val projectMethods = LiveMapByFiles(project) {
        if (enabled) findUsages(it.fileScope())
        else emptyList()
    }

    private val annotationClass by cachedIfEnabled(JaicfVersionTracker.getInstance(project)) {
        findClass(PLUGIN_PACKAGE, PATH_ARGUMENT_ANNOTATION_NAME, project)
    }

    private fun findUsages(scope: SearchScope): List<KtFunction> {
        return annotationClass?.search(scope)
            ?.mapNotNull { it.element.getParentOfType<KtFunction>(true) }
            ?.distinct() ?: emptyList()
    }

    companion object {
        fun getInstance(project: Project): PathValueMethodsService =
            project.getService(PathValueMethodsService::class.java)
    }
}
