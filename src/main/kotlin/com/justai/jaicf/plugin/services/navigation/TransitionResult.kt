package com.justai.jaicf.plugin.services.navigation

import com.justai.jaicf.plugin.services.managers.dto.State
import com.justai.jaicf.plugin.services.navigation.TransitionResult.StateFound
import com.justai.jaicf.plugin.services.navigation.TransitionResult.StatesFound
import com.justai.jaicf.plugin.services.navigation.TransitionResult.SuggestionsFound

sealed class TransitionResult {
    object OutOfStateBoundUsage : TransitionResult()
    object UnresolvedPath : TransitionResult()
    object NoState : TransitionResult()
    data class StateFound(val state: State) : TransitionResult()
    data class StatesFound(val states: List<State>) : TransitionResult()
    data class SuggestionsFound(val suggestionsStates: List<State>) : TransitionResult()
}

fun TransitionResult.firstStateOrSuggestion() = when (this) {
    is StateFound -> state
    is StatesFound -> states.firstOrNull()
    is SuggestionsFound -> suggestionsStates.firstOrNull()
    else -> null
}

fun TransitionResult.statesOrSuggestions() = when (this) {
    is StateFound -> listOf(state)
    is StatesFound -> states
    is SuggestionsFound -> suggestionsStates
    else -> emptyList()
}

fun TransitionResult.states() = when (this) {
    is StateFound -> listOf(state)
    is StatesFound -> states
    else -> emptyList()
}
