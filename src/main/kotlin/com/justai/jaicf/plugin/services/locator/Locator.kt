package com.justai.jaicf.plugin.services.locator

import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.isRemoved
import com.justai.jaicf.plugin.services.managers.ScenarioDataManager
import com.justai.jaicf.plugin.services.managers.dto.State
import org.jetbrains.kotlin.psi.KtFile

val PsiElement.framingState: State?
    get() {
        if (isRemoved)
            return null

        val file = this.containingFile as? KtFile ?: return null
        val scenarios1 = ScenarioDataManager[this]?.getScenarios(file)
        val scenarios = scenarios1 ?: return null

        return scenarios
            .firstOrNull { contains(it.innerState, this) }
            ?.let { recursiveFindState(it.innerState, this) }
    }

private fun contains(state: State, element: PsiElement) =
    element.textRange.startOffset in state.stateExpression.textRange

private fun recursiveFindState(state: State, element: PsiElement): State =
    state.states.firstOrNull { contains(it, element) }?.let { recursiveFindState(it, element) } ?: state