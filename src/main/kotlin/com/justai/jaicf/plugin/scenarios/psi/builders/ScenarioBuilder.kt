package com.justai.jaicf.plugin.scenarios.psi.builders

import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.scenarios.psi.dto.Scenario
import org.jetbrains.kotlin.psi.KtCallExpression

fun buildScenario(body: KtCallExpression) =
    if (body.isExist)
        Scenario(body.project, body.containingKtFile).run {
            innerState = buildState(body, this) ?: return@run null
            this
        }
    else
        null
