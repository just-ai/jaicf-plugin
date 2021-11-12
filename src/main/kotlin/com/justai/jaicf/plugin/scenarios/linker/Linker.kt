package com.justai.jaicf.plugin.scenarios.linker

import com.intellij.openapi.project.Project
import com.justai.jaicf.plugin.scenarios.psi.StatePathExpressionsService
import com.justai.jaicf.plugin.scenarios.psi.ScenarioDataService
import com.justai.jaicf.plugin.scenarios.psi.TopLevelAppendDataService
import com.justai.jaicf.plugin.scenarios.psi.dto.Scenario
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.TopLevelAppend
import com.justai.jaicf.plugin.scenarios.psi.dto.isRootState
import com.justai.jaicf.plugin.scenarios.psi.dto.nestedStates
import com.justai.jaicf.plugin.scenarios.psi.dto.receiverExpression
import com.justai.jaicf.plugin.scenarios.transition.statesOrSuggestions
import com.justai.jaicf.plugin.scenarios.transition.transitToState
import com.justai.jaicf.plugin.utils.StatePathExpression
import com.justai.jaicf.plugin.utils.isExist

val State.allStates
    get() = allStates()

val State.usages: List<StatePathExpression>
    get() {
        val expressionsService = StatePathExpressionsService.getInstance(project)
        return expressionsService.getExpressions()
            .filter { this in transitToState(it.usePoint, it.declaration).statesOrSuggestions() }
    }

val Scenario.appendingStates: List<State>
    get() {
        val resolver = ScenarioReferenceResolver.getInstance(project)
        return ScenarioDataService.getInstance(project).getScenarios().flatMap { it.allAppends }
            .filter { (reference, state) -> reference != null && resolver.resolve(reference, state) == this }
            .map { it.second }
    }

val Project.rootScenarios: List<Scenario>
    get() {
        val resolver = ScenarioReferenceResolver.getInstance(this)
        val scenarios = ScenarioDataService.getInstance(this).getScenarios()
        val appendingScenarios = scenarios
            .flatMap { it.allAppends }
            .mapNotNull { (reference, _) -> reference?.let { resolver.resolve(it) } }

        return scenarios - appendingScenarios
    }

val Scenario.allAppends
    get() = nestedStates.flatMap { state -> state.appends.map { it.referenceToScenario to state } } +
        TopLevelAppendDataService.getInstance(project).getAppends().map { it.referenceToScenario to innerState }

private fun State.allStates(previousStates: MutableList<State> = mutableListOf()): List<State> {
    if (this in previousStates) return emptyList()

    previousStates += this

    val statesList = states.toMutableList()
    statesList += appends.flatMap { it.scenario?.innerState?.allStates(previousStates) ?: emptyList() }
    if (this.isRootState) {
        statesList += scenario.appends.filter { it.referenceToScenario?.isExist == true }
            .flatMap { it.scenario?.innerState?.allStates(previousStates) ?: emptyList() }
    }

    return statesList
}

val Scenario.appends: List<TopLevelAppend>
    get() {
        val resolver = ScenarioReferenceResolver.getInstance(project)
        return TopLevelAppendDataService.getInstance(project).getAppends()
            .filter { append -> append.receiverExpression?.let { resolver.resolve(it) } == this }
    }
