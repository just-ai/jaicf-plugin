package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.justai.jaicf.plugin.RecursiveSafeValue
import com.justai.jaicf.plugin.State
import com.justai.jaicf.plugin.TransitionResult
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.pathExpressionsOfBoundedBlock
import com.justai.jaicf.plugin.search
import com.justai.jaicf.plugin.statesOrSuggestions
import com.justai.jaicf.plugin.transitToState
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

class StateUsagesSearchService(val project: Project) : Service {

    private var modifiedFiles = setOf<KtFile>()
    private val expressionService = ExpressionService[project]

    private val expressionsToTransitions =
        RecursiveSafeValue(mapOf<KtExpression, TransitionResult>()) {
            expressionService.expressions.associateWith { transitToState(it) }.also {
                modifiedFiles = emptySet()
            }
        }

    fun findStateUsages(state: State): List<KtExpression> = expressionsToTransitions.value
        .filter { (_, transition) -> state in transition.statesOrSuggestions() }
        .map { it.key }

    override fun markFileAsModified(file: KtFile) {
        if (file !in modifiedFiles)
            modifiedFiles = modifiedFiles + file

        expressionsToTransitions.invalid()
    }

    companion object {
        operator fun get(element: PsiElement): StateUsagesSearchService? =
            if (element.isExist) ServiceManager.getService(element.project, StateUsagesSearchService::class.java)
            else null
    }
}

class ExpressionService(val project: Project) : Service {

    val expressions: List<KtExpression>
        get() = expressionsByFiles.value.values.flatten()

    private var modifiedFiles = setOf<KtFile>()

    private val expressionsByFiles by lazy {
        RecursiveSafeValue(findExpressions(project.projectScope()).groupBy { it.containingKtFile }) { map ->
            val unmodifiedFiles = (map.keys - modifiedFiles).filter { it.isExist }
            val scopeOfModifiedFiles = modifiedFiles.map { it.fileScope() }.reduce { acc, scope -> acc.union(scope) }

            (unmodifiedFiles.associateWith { map[it]!! } +
                    findExpressions(scopeOfModifiedFiles).groupBy { it.containingKtFile })
                .filter { it.value.isNotEmpty() }
                .also { modifiedFiles = emptySet() }
        }
    }

    private fun findExpressions(scope: GlobalSearchScope): List<KtExpression> {
        val methods = JumpMethodsService[project].getMethods()

        return methods
            .flatMap { it.search(scope) }
            .map { it.element }
            .flatMap { it.pathExpressionsOfBoundedBlock }
    }

    override fun markFileAsModified(file: KtFile) {
        if (file !in modifiedFiles)
            modifiedFiles = modifiedFiles + file

        expressionsByFiles.invalid()
    }

    companion object {
        operator fun get(project: Project): ExpressionService =
            ServiceManager.getService(project, ExpressionService::class.java)
    }
}