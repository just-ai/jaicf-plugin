package com.justai.jaicf.plugin.scenarios.psi.dto

import com.intellij.openapi.project.Project
import com.justai.jaicf.plugin.utils.argumentExpression
import com.justai.jaicf.plugin.utils.getRootDotReceiver
import com.justai.jaicf.plugin.utils.APPEND_OTHER_ARGUMENT_NAME
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtReferenceExpression

class TopLevelAppend(
    val project: Project,
    val callExpression: KtCallExpression,
    val dotExpression: KtDotQualifiedExpression,
) {
    val textHashCode: Int = dotExpression.text.hashCode()
    val file: KtFile = callExpression.containingKtFile

    val referenceToScenario: KtReferenceExpression?
        get() = callExpression.argumentExpression(APPEND_OTHER_ARGUMENT_NAME) as? KtReferenceExpression
}

val TopLevelAppend.receiverExpression get() = dotExpression.getRootDotReceiver()
