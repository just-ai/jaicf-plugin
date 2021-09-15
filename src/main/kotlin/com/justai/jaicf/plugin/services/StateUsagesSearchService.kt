package com.justai.jaicf.plugin.services

import com.justai.jaicf.plugin.services.managers.PathValueExpressionManager
import com.justai.jaicf.plugin.services.managers.dto.State
import com.justai.jaicf.plugin.services.navigation.statesOrSuggestions
import com.justai.jaicf.plugin.services.navigation.transitToState
import org.jetbrains.kotlin.psi.KtExpression


val State.usages: List<KtExpression>
    get() {
        val expressionManager = PathValueExpressionManager[project]
        return expressionManager.getExpressions()
            .filter { this in transitToState(it).statesOrSuggestions() }
    }
