package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.justai.jaicf.plugin.REACTIONS_CHANGE_STATE_METHOD_NAME
import com.justai.jaicf.plugin.REACTIONS_CLASS_NAME
import com.justai.jaicf.plugin.REACTIONS_GO_METHOD_NAME
import com.justai.jaicf.plugin.REACTIONS_PACKAGE
import com.justai.jaicf.plugin.RecursiveSafeValue
import com.justai.jaicf.plugin.State
import com.justai.jaicf.plugin.TransitionResult
import com.justai.jaicf.plugin.findClass
import com.justai.jaicf.plugin.firstStateOrSuggestion
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.pathExpressionsOfBoundedBlock
import com.justai.jaicf.plugin.transitToState
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

class StateUsagesSearchService(val project: Project) : Service {

    private var modifiedFiles: List<KtFile> = emptyList()
    private val predefinedJumpReactions by lazy { findPredefinedJumpReactions() }
    private val expressionsToTransitions =
        RecursiveSafeValue(mapOf<KtExpression, TransitionResult>(), true) {
            expressionsByFiles.value.values.flatten().associateWith { transitToState(it) }
        }
    private val expressionsByFiles by lazy {
        RecursiveSafeValue(findExpressions(project.projectScope()).groupBy { it.containingKtFile }, true) { map ->
            val unmodifiedFiles = map.keys - modifiedFiles
            (unmodifiedFiles.associateWith { map[it]!! } + modifiedFiles.associateWith { findExpressions(it.fileScope()) })
                .filter { it.value.isNotEmpty() }
                .also { modifiedFiles = emptyList() }
        }
    }

    fun findStateUsages(state: State): List<KtExpression> = expressionsToTransitions.value
        .filter { (_, transition) -> transition.firstStateOrSuggestion() == state }
        .map { it.key }

    override fun markFileAsModified(file: KtFile) {
        modifiedFiles = modifiedFiles + file
        expressionsToTransitions.invalid()
        expressionsByFiles.invalid()
    }

    private fun findExpressions(scope: GlobalSearchScope): List<KtExpression> {
        val methods = PathValueSearcher[project].getMethodsUsedPathValue()
            .ifEmpty { predefinedJumpReactions }

        return methods
            .filter { it.isExist }
            .flatMap { ReferencesSearch.search(it, scope, true).findAll() }
            .map { it.element }
            .flatMap { it.pathExpressionsOfBoundedBlock }
    }

    private fun findPredefinedJumpReactions(): List<PsiMethod> {
        if (DumbService.getInstance(project).isDumb)
            throw ProcessCanceledException()

        val reactionsClass = findClass(REACTIONS_PACKAGE, REACTIONS_CLASS_NAME, project) ?: return emptyList()

        return reactionsClass.allMethods.filter {
            it.name == REACTIONS_GO_METHOD_NAME || it.name == REACTIONS_CHANGE_STATE_METHOD_NAME
        }
    }

    companion object {
        operator fun get(element: PsiElement): StateUsagesSearchService? =
            if (element.isExist) ServiceManager.getService(element.project, StateUsagesSearchService::class.java)
            else null
    }
}