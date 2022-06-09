package com.justai.jaicf.plugin.core.psi.dto

import com.intellij.openapi.project.Project
import com.justai.jaicf.plugin.core.linker.ScenarioReferenceResolver
import com.justai.jaicf.plugin.utils.APPEND_OTHER_ARGUMENT_NAME
import com.justai.jaicf.plugin.utils.argumentExpression
import com.justai.jaicf.plugin.utils.measure
import com.justai.jaicf.plugin.utils.rootReceiverExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtReferenceExpression

interface Append {
    val project: Project
    val referenceToScenario: KtReferenceExpression?
    val callExpression: KtCallExpression
    val parentState: State?
}

data class NestedAppend(
    override val project: Project,
    override val referenceToScenario: KtReferenceExpression,
    override val callExpression: KtCallExpression,
    override val parentState: State? = null,
) : Append

class TopLevelAppend(
    override val project: Project,
    override val callExpression: KtCallExpression,
    val dotExpression: KtDotQualifiedExpression,
) : Append {
    val file: KtFile = callExpression.containingKtFile

    override val referenceToScenario: KtReferenceExpression?
        get() = callExpression.argumentExpression(APPEND_OTHER_ARGUMENT_NAME) as? KtReferenceExpression

    override val parentState: State?
        get() = project.measure("TopLevelAppend.parentState") {receiverExpression?.let { ScenarioReferenceResolver.getInstance(project).resolve(it)?.innerState }}
}

val TopLevelAppend.receiverExpression get() = dotExpression.rootReceiverExpression
