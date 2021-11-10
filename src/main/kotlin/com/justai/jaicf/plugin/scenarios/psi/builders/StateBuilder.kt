package com.justai.jaicf.plugin.scenarios.psi.builders

import com.justai.jaicf.plugin.scenarios.psi.dto.Scenario
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.StateIdentifier
import com.justai.jaicf.plugin.scenarios.psi.dto.StateIdentifier.ExpressionIdentifier
import com.justai.jaicf.plugin.scenarios.psi.dto.StateIdentifier.NoIdentifier
import com.justai.jaicf.plugin.scenarios.psi.dto.StateIdentifier.PredefinedIdentifier
import com.justai.jaicf.plugin.utils.STATE_BODY_ANNOTATION_NAME
import com.justai.jaicf.plugin.utils.STATE_DECLARATION_ANNOTATION_NAME
import com.justai.jaicf.plugin.utils.STATE_NAME_ANNOTATION_ARGUMENT_NAME
import com.justai.jaicf.plugin.utils.STATE_NAME_ANNOTATION_NAME
import com.justai.jaicf.plugin.utils.argumentExpression
import com.justai.jaicf.plugin.utils.argumentExpressionsByAnnotation
import com.justai.jaicf.plugin.utils.argumentExpressionsOrDefaultValuesByAnnotation
import com.justai.jaicf.plugin.utils.declaration
import com.justai.jaicf.plugin.utils.findChildOfType
import com.justai.jaicf.plugin.utils.findChildrenOfType
import com.justai.jaicf.plugin.utils.getMethodAnnotations
import com.justai.jaicf.plugin.utils.isRemoved
import com.justai.jaicf.plugin.utils.stringValueOrNull
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

fun buildState(
    stateExpression: KtCallExpression,
    scenario: Scenario,
    parentState: State? = null,
): State? {
    if (stateExpression.isRemoved || !stateExpression.isStateDeclaration) return null

    val identifier = stateExpression.identifierOfStateExpression

    return State(stateExpression.project, identifier, parentState, scenario, stateExpression)
        .apply {
            val statements = stateExpression.statements
            appends = statements.mapNotNull { buildAppend(it, this) }
            states = statements.mapNotNull { buildState(it, scenario, this) }
        }
}

private val KtCallExpression.statements: List<KtCallExpression>
    get() {
        val body = this.annotatedLambdaArgument ?: this.annotatedLambdaBlockInDeclaration

        return body?.bodyExpression?.statements?.filterIsInstance<KtCallExpression>() ?: emptyList()
    }

private val KtCallExpression.annotatedLambdaArgument: KtLambdaExpression?
    get() = this.argumentExpressionsOrDefaultValuesByAnnotation(STATE_BODY_ANNOTATION_NAME).firstIsInstanceOrNull()

val KtCallExpression.annotatedLambdaBlockInDeclaration: KtLambdaExpression?
    get() = this.declaration?.bodyExpression?.findChildOfType<KtAnnotatedExpression>()?.baseExpression as? KtLambdaExpression

fun KtCallExpression.getAnnotatedStringTemplatesInDeclaration(name: String): List<KtStringTemplateExpression> {
    val bodyExpression = this.declaration?.bodyExpression ?: return emptyList()

    val annotationsExpressions = bodyExpression.findChildrenOfType<KtAnnotatedExpression>().filter {
        it.annotationEntries.any { entry -> entry.shortName?.asString() == name }
    }

    return annotationsExpressions.mapNotNull { it.baseExpression as? KtStringTemplateExpression }
}

val KtCallExpression.isStateDeclaration: Boolean
    get() = getMethodAnnotations(STATE_DECLARATION_ANNOTATION_NAME).isNotEmpty()

private val KtCallExpression.identifierOfStateExpression: StateIdentifier
    get() {
        val stateNameExpression = argumentExpressionsByAnnotation(STATE_NAME_ANNOTATION_NAME).firstOrNull()
        if (stateNameExpression != null) {
            return ExpressionIdentifier(stateNameExpression, stateNameExpression.parent)
        }

        val defaultExpression = argumentExpressionsOrDefaultValuesByAnnotation(STATE_NAME_ANNOTATION_NAME).firstOrNull()
        if (defaultExpression != null) {
            return ExpressionIdentifier(defaultExpression, defaultExpression.parent)
        }

        val stateAnnotation = getMethodAnnotations(STATE_DECLARATION_ANNOTATION_NAME).last()

        val stateName = stateAnnotation.argumentExpression(STATE_NAME_ANNOTATION_ARGUMENT_NAME)?.stringValueOrNull

        return stateName?.let { PredefinedIdentifier(it, this) }
            ?: return NoIdentifier(
                this,
                "The state name is not defined. Use @$STATE_NAME_ANNOTATION_NAME or specify $STATE_NAME_ANNOTATION_ARGUMENT_NAME of @$STATE_DECLARATION_ANNOTATION_NAME."
            )
    }
