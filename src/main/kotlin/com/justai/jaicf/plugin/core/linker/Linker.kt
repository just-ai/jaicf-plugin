package com.justai.jaicf.plugin.core.linker

import com.intellij.openapi.project.Project
import com.justai.jaicf.plugin.core.psi.dto.NestedAppend
import com.justai.jaicf.plugin.core.psi.dto.Scenario
import com.justai.jaicf.plugin.core.psi.dto.State
import com.justai.jaicf.plugin.core.psi.dto.TopLevelAppend
import com.justai.jaicf.plugin.core.psi.dto.isRootState
import com.justai.jaicf.plugin.core.psi.dto.name
import com.justai.jaicf.plugin.core.psi.dto.nestedStates
import com.justai.jaicf.plugin.core.psi.dto.receiverExpression
import com.justai.jaicf.plugin.core.psi.pathValueExpressions
import com.justai.jaicf.plugin.core.psi.scenarios
import com.justai.jaicf.plugin.core.psi.topLevelAppends
import com.justai.jaicf.plugin.core.transition.statesOrSuggestions
import com.justai.jaicf.plugin.core.transition.transitToState
import com.justai.jaicf.plugin.utils.StatePathExpression
import com.justai.jaicf.plugin.utils.isExist
import com.justai.jaicf.plugin.utils.measure

val State.allStates
    get() = project.measure({ "State($name).allStates" }) {
        allStates().toList()
    }

val State.sequenceAllStates
    get() = project.measure({ "State($name).sequenceAllStates" }) {
        allStates()
    }

val State.usages: List<StatePathExpression>
    get() = project.measure("State($name).usages") {
        pathValueExpressions
            .filter { this@usages in transitToState(it.usePoint, it.declaration).statesOrSuggestions() }
    }

val Scenario.appendingStates: List<State>
    get() = project.measure({ "Scenario($name).appendingStates" }) {
        project.appends
            .filter { it.scenario == this@appendingStates }
            .mapNotNull { it.parentState }
    }

val Project.rootScenarios: List<Scenario>
    get() = measure("Project.rootScenarios") {
        val appendedScenarios = appends.mapNotNull { it.scenario }.toSet()
        return@measure scenarios - appendedScenarios
    }

private val Project.appends
    get() = measure("Project.rootScenarios") {
        scenarios.flatMap { it.nestedAppends } + topLevelAppends
    }

val Scenario.allAppends
    get() = project.measure("Scenario($name).allAppends") {
        nestedAppends + topLevelAppends
    }

private fun State.allStates(previousStates: MutableSet<State> = mutableSetOf()): Sequence<State> {
    if (this in previousStates) return emptySequence()

    previousStates += this

    var statesList = states.asSequence()
    statesList += nestedAppends.asSequence()
        .flatMap { it.scenario?.innerState?.allStates(previousStates) ?: emptySequence() }
    if (this.isRootState) {
        statesList += scenario.topLevelAppends.asSequence().filter { it.referenceToScenario?.isExist == true }
            .flatMap { it.scenario?.innerState?.allStates(previousStates) ?: emptySequence() }
    }

    return statesList
}

val Scenario.topLevelAppends: List<TopLevelAppend>
    get() = project.measure("Scenario.topLevelAppends") {
        val resolver = ScenarioReferenceResolver.getInstance(project)
        project.topLevelAppends.filter { append ->
            append.receiverExpression?.let { resolver.resolve(it) } == this@topLevelAppends
        }
    }

val Scenario.nestedAppends: List<NestedAppend>
    get() = project.measure("Scenario.nestedAppends") {
        nestedStates.flatMap { state -> state.nestedAppends }
    }
