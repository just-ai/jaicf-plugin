package com.justai.jaicf.plugin.scenarios.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.justai.jaicf.plugin.scenarios.JaicfService
import com.justai.jaicf.plugin.trackers.JaicfVersionTracker
import com.justai.jaicf.plugin.utils.LiveMapByFiles
import com.justai.jaicf.plugin.utils.PATH_ARGUMENT_ANNOTATION_NAME
import com.justai.jaicf.plugin.utils.PLUGIN_PACKAGE
import com.justai.jaicf.plugin.utils.findChildrenOfType
import com.justai.jaicf.plugin.utils.isExist
import com.justai.jaicf.plugin.utils.measure
import com.justai.jaicf.plugin.utils.pathExpressionsOfBoundedBlock
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.search.allScope
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.idea.search.minus
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class PathValueExpressionsService(project: Project) : JaicfService(project) {

    private val pathValueService = MethodsUsedPathValueService(project)

    private val map = ConcurrentHashMap<KtFile, Set<PsiElement>>()
    private val expressionsMap = LiveMapByFiles(project) { file ->
        val children: Set<PsiElement> =
            measure("children") { (file.findChildrenOfType<KtOperationReferenceExpression>() + file.findChildrenOfType<KtNameReferenceExpression>()).toSet() }
        val ktElements = map[file]
        if (ktElements == children)
            return@LiveMapByFiles ktElements.flatMap { it.pathExpressionsOfBoundedBlock }
        else
            println("${children.map { it.text }}\n${ktElements?.map { it.text }}")

        map[file] = children.toSet()

        pathValueService.jaicfMethods
            .flatMap { it.search(file.fileScope()) }
            .map { it.element }
            .flatMap { it.pathExpressionsOfBoundedBlock }
    }

    fun getExpressions() =
        measure("PathValueExpressionsService.getExpressions") { expressionsMap.getNotNullValues().flatten() }

    companion object {
        fun getInstance(element: PsiElement): PathValueExpressionsService? =
            if (element.isExist) getInstance(element.project)
            else null

        fun getInstance(project: Project): PathValueExpressionsService =
            project.getService(PathValueExpressionsService::class.java)
    }
}

class MethodsUsedPathValueService(project: Project) : JaicfService(project) {

    val jaicfMethods
        get() = (jaicfCoreMethods + jaicfProjectMethods.getNotNullValues().flatten()).filter { it.isExist }

    val jaicfCoreMethods: List<KtFunction> by cached(LibraryModificationTracker.getInstance(project)) {
        measure("jaicfCoreMethods") {
            if (enabled) findUsages(project.allScope() - project.projectScope())
            else emptyList()
        }
    }

    private val jaicfProjectMethods = LiveMapByFiles(project) {
        if (enabled) findUsages(it.fileScope())
        else emptyList()
    }

    private val annotationClass by cachedIfEnabled(JaicfVersionTracker.getInstance(project)) {
        findClass(PLUGIN_PACKAGE, PATH_ARGUMENT_ANNOTATION_NAME, project)
    }

    private fun findUsages(scope: SearchScope): List<KtFunction> {
        return measure("findUsages($scope)") {
            annotationClass?.search(scope)
                ?.mapNotNull { it.element.getParentOfType<KtFunction>(true) }
                ?.distinct() ?: emptyList()
        }
    }

    companion object {
        fun getInstance(project: Project): MethodsUsedPathValueService =
            project.getService(MethodsUsedPathValueService::class.java)
    }
}

val Project.pathValueExpressions get() = PathValueExpressionsService.getInstance(this).getExpressions()
