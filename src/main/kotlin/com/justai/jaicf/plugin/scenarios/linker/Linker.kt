package com.justai.jaicf.plugin.scenarios.linker

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker.NEVER_CHANGED
import com.justai.jaicf.plugin.scenarios.psi.PathValueExpressionsService
import com.justai.jaicf.plugin.scenarios.psi.dto.NestedAppend
import com.justai.jaicf.plugin.scenarios.psi.dto.Scenario
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.TopLevelAppend
import com.justai.jaicf.plugin.scenarios.psi.dto.isRootState
import com.justai.jaicf.plugin.scenarios.psi.dto.name
import com.justai.jaicf.plugin.scenarios.psi.dto.nestedStates
import com.justai.jaicf.plugin.scenarios.psi.dto.receiverExpression
import com.justai.jaicf.plugin.scenarios.psi.pathValueExpressions
import com.justai.jaicf.plugin.scenarios.psi.scenarios
import com.justai.jaicf.plugin.scenarios.psi.topLevelAppends
import com.justai.jaicf.plugin.scenarios.transition.statesOrSuggestions
import com.justai.jaicf.plugin.scenarios.transition.transitToState
import com.justai.jaicf.plugin.services.caching
import com.justai.jaicf.plugin.utils.StatePathExpression
import com.justai.jaicf.plugin.utils.isExist
import com.justai.jaicf.plugin.utils.measure
import kotlin.streams.toList

val State.allStates by caching({ project }) {
    project.measure({ "State($name).allStates" }) {
        allStates().toList()
    }
}
val State.sequenceAllStates get() = project.measure({ "State($name).sequenceAllStates" }) {
        allStates()
    }


val State.usages: List<StatePathExpression>
    get() = project.measure("State(${this.name}).usages") {
        return@measure pathValueExpressions
            .filter { this@usages in transitToState(it.usePoint, it.declaration).statesOrSuggestions() }
    }

val Scenario.appendingStates: List<State> by caching({ project }) {
    project.measure({ "Scenario($name).appendingStates" }) {
        project.appends
            .filter { it.scenario == this@caching }
            .mapNotNull { it.parentState }
    }
}

val Project.rootScenarios: List<Scenario> by caching({ this }) {
    measure("Project.rootScenarios") {
        val appendedScenarios = appends.mapNotNull { it.scenario }.toSet()
        return@measure scenarios - appendedScenarios
    }
}

private val Project.appends get() = scenarios.flatMap { it.nestedAppends } + topLevelAppends

val Scenario.allAppends by caching({ project }) {
    project.measure("Scenario($name).allAppends") {
        nestedAppends + topLevelAppends
    }
}

private fun State.allStates(previousStates: MutableSet<State> = mutableSetOf()): Sequence<State> {
    if (this in previousStates) return emptySequence()

    previousStates += this

    var statesList = states.asSequence()
    statesList += nestedAppends.asSequence().flatMap { it.scenario?.innerState?.allStates(previousStates) ?: emptySequence() }
    if (this.isRootState) {
        statesList += scenario.topLevelAppends.asSequence().filter { it.referenceToScenario?.isExist == true }
            .flatMap { it.scenario?.innerState?.allStates(previousStates) ?: emptySequence() }
    }

    return statesList
}

val Scenario.topLevelAppends: List<TopLevelAppend> by caching({ project }) {
    val resolver = ScenarioReferenceResolver.getInstance(project)
    project.topLevelAppends.filter { append ->
        append.receiverExpression?.let { resolver.resolve(it) } == this
    }
}

val Scenario.nestedAppends: List<NestedAppend> by caching(NEVER_CHANGED) { nestedStates.flatMap { state -> state.nestedAppends } }
