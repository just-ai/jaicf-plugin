package com.justai.jaicf.plugin.services.managers.dto

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression

data class Append(
    val project: Project,
    val referenceToScenario: KtReferenceExpression,
    val callExpression: KtCallExpression,
    val parentState: State? = null,
)
