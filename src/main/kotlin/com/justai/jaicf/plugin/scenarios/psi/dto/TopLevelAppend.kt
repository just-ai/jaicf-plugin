package com.justai.jaicf.plugin.scenarios.psi.dto

import com.intellij.openapi.project.Project
import com.justai.jaicf.plugin.scenarios.psi.TopLevelAppendDataService
import com.justai.jaicf.plugin.scenarios.psi.builders.buildTopLevelAppend
import com.justai.jaicf.plugin.utils.APPEND_OTHER_ARGUMENT_NAME
import com.justai.jaicf.plugin.utils.argumentExpression
import com.justai.jaicf.plugin.utils.rootReceiverExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtReferenceExpression

/**
 * Внутренне представление вызовов метода `append` как extension метода.
 *
 * @property project проект в котором находится вызов метода
 * @property callExpression объект выражения являющиеся вызовов метода без ресивера
 * @property dotExpression объект содержащий вызов метода вместе с ресивером
 * @property file где находится вызов
 * @property referenceToScenario ссылка на сценарий переданный в качестве аргумента в метод
 *
 * @see buildTopLevelAppend
 * @see TopLevelAppendDataService
 */
class TopLevelAppend(
    val project: Project,
    private val callExpression: KtCallExpression,
    val dotExpression: KtDotQualifiedExpression,
) {
    val file: KtFile = callExpression.containingKtFile

    val referenceToScenario: KtReferenceExpression?
        get() = callExpression.argumentExpression(APPEND_OTHER_ARGUMENT_NAME) as? KtReferenceExpression
}

val TopLevelAppend.receiverExpression get() = dotExpression.rootReceiverExpression
