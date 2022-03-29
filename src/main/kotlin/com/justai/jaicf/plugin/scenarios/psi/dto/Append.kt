package com.justai.jaicf.plugin.scenarios.psi.dto

import com.intellij.openapi.project.Project
import com.justai.jaicf.plugin.scenarios.psi.builders.buildAppend
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression

/**
 * Внутреннее представление вызова метода `append` находящегося внутри a state. Для extension вызовов метода `append` смотрите [TopLevelAppend].
 *
 * @property project проект в котором находится вызов метода
 * @property referenceToScenario ссылка на сценарий переданный в качестве аргумента `other`
 * @property callExpression объект выражения содержащего вызов метода
 * @property parentState стейт внутри которого происходит вызов метода append
 *
 * @see buildAppend
 */
data class Append(
    val project: Project,
    val referenceToScenario: KtReferenceExpression,
    val callExpression: KtCallExpression,
    val parentState: State,
)
