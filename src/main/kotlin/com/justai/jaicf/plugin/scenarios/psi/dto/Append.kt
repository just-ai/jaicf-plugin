package com.justai.jaicf.plugin.scenarios.psi.dto

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression

data class Append(
    val project: Project,
    val referenceToScenario: KtReferenceExpression,
    val callExpression: KtCallExpression,
    val parentState: State? = null,
)
