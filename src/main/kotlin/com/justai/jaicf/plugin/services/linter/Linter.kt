package com.justai.jaicf.plugin.services.linter

import com.intellij.openapi.project.Project
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.services.managers.ScenarioDataManager
import com.justai.jaicf.plugin.services.managers.TopLevelAppendDataManager
import com.justai.jaicf.plugin.services.managers.dto.Scenario
import com.justai.jaicf.plugin.services.managers.dto.State
import com.justai.jaicf.plugin.services.managers.dto.TopLevelAppend
import com.justai.jaicf.plugin.services.managers.dto.isRootState
import com.justai.jaicf.plugin.services.managers.dto.nestedStates
import com.justai.jaicf.plugin.services.managers.dto.receiverExpression

val State.allStates
    get() = allStates()

val Scenario.appendingStates: List<State>
    get() {
        val resolver = ScenarioReferenceResolver[project]
        return ScenarioDataManager[project].getScenarios().flatMap { it.allAppends }
            .filter { (reference, state) -> reference != null && resolver.resolve(reference, state) == this }
            .map { it.second }
    }


val Project.rootScenarios: List<Scenario>
    get() {
        val resolver = ScenarioReferenceResolver[this]
        val scenarios = ScenarioDataManager[this].getScenarios()
        val appendingScenarios = scenarios
            .flatMap { it.allAppends }
            .mapNotNull { (reference, _) -> reference?.let { resolver.resolve(it) } }

        return scenarios - appendingScenarios
    }

val Scenario.allAppends
    get() = nestedStates.flatMap { state -> state.appends.map { it.referenceToScenario to state } } +
            TopLevelAppendDataManager[project].getAppends().map { it.referenceToScenario to innerState }


private fun State.allStates(previousStates: MutableList<State> = mutableListOf()): List<State> {
    if (this in previousStates) {
        return emptyList()
    }

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
        val resolver = ScenarioReferenceResolver[project]
        return TopLevelAppendDataManager[project].getAppends()
            .filter { append -> append.receiverExpression?.let { resolver.resolve(it) } == this }
    }
