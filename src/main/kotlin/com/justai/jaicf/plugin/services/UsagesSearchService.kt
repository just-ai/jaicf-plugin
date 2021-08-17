package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.ReferencesSearch
import com.justai.jaicf.plugin.LazySafeValue
import com.justai.jaicf.plugin.REACTIONS_CHANGE_STATE_METHOD_NAME
import com.justai.jaicf.plugin.REACTIONS_CLASS_NAME
import com.justai.jaicf.plugin.REACTIONS_GO_METHOD_NAME
import com.justai.jaicf.plugin.REACTIONS_PACKAGE
import com.justai.jaicf.plugin.State
import com.justai.jaicf.plugin.findClass
import com.justai.jaicf.plugin.firstStateOrSuggestion
import com.justai.jaicf.plugin.getPathExpressionsOfBoundedBlock
import com.justai.jaicf.plugin.transitToState
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

class UsagesSearchService(val project: Project) {

    private val predefinedJumpReactions by lazy { findPredefinedJumpReactions() }
    private val pathExpressions = LazySafeValue(emptyList<KtExpression>()) { findExpressions() }

    fun findStateUsages(state: State): List<KtExpression> {
        if (pathExpressions.value.isEmpty())
            Logger.getInstance("findStateUsages").warn("No path expression was found in project")

        return pathExpressions.value
            .filter { transitToState(it).firstStateOrSuggestion() == state }
    }

    fun markFileAsModified(file: KtFile) {
        pathExpressions.lazyUpdate { findExpressions() }
    }

    private fun findPredefinedJumpReactions(): List<PsiMethod> {
        check(!DumbService.getInstance(project).isDumb) { "IDEA in dumb mode" }

        val reactionsClass = findClass(REACTIONS_PACKAGE, REACTIONS_CLASS_NAME, project) ?: return emptyList()
        val jumpMethods = reactionsClass.allMethods.filter {
            it.name == REACTIONS_GO_METHOD_NAME || it.name == REACTIONS_CHANGE_STATE_METHOD_NAME
        }

        if (jumpMethods.isEmpty()) {
            logger.warn("No jump method declaration was found")
            return emptyList()
        }

        return jumpMethods
    }

    private fun findExpressions(): List<KtExpression> {
        val methods = SearchService.get(project).getMethodsUsedPathValue()
            .ifEmpty { predefinedJumpReactions }
            .ifEmpty { return emptyList() }

        return methods
            .flatMap { ReferencesSearch.search(it, project.projectScope()).findAll() }
            .map { it.element }
            .flatMap { it.getPathExpressionsOfBoundedBlock() }
    }

    companion object {
        private val logger = Logger.getInstance(this::class.java)

        fun get(project: Project): UsagesSearchService =
            ServiceManager.getService(project, UsagesSearchService::class.java)
    }
}