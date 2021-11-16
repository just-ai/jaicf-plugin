package com.justai.jaicf.plugin.scenarios.linker

import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.scenarios.psi.ScenarioDataService
import com.justai.jaicf.plugin.scenarios.psi.dto.Scenario
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.utils.isRemoved
import org.jetbrains.kotlin.psi.KtFile

val PsiElement.framingState: State?
    get() {
        if (isRemoved)
            return null

        val file = this.containingFile as? KtFile ?: return null
        val scenarios = ScenarioDataService.getInstance(this)?.getScenarios(file) ?: return null

        return scenarios.findBoundingScenario(this)
            ?.let { recursiveFindState(it.innerState, this) }
    }

fun List<Scenario>.findBoundingScenario(element: PsiElement) = filter { contains(it.innerState, element) }
    .minByOrNull { it.declarationElement.text.length }

private fun contains(state: State, element: PsiElement) =
    element.textRange.startOffset in state.stateExpression.textRange

private fun recursiveFindState(state: State, element: PsiElement): State =
    state.states.firstOrNull { contains(it, element) }?.let { recursiveFindState(it, element) } ?: state
