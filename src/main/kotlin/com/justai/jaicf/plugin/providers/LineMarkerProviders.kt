package com.justai.jaicf.plugin.providers

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.justai.jaicf.plugin.asLeaf
import com.justai.jaicf.plugin.findChildOfType
import com.justai.jaicf.plugin.getBoundedCallExpressionOrNull
import com.justai.jaicf.plugin.holderExpression
import com.justai.jaicf.plugin.pathExpressionsOfBoundedBlock
import com.justai.jaicf.plugin.providers.Icons.GO_TO_STATES
import com.justai.jaicf.plugin.scenarios.linter.framingState
import com.justai.jaicf.plugin.scenarios.linter.usages
import com.justai.jaicf.plugin.scenarios.psi.builders.isStateDeclaration
import com.justai.jaicf.plugin.scenarios.psi.dto.name
import com.justai.jaicf.plugin.scenarios.transition.absolutePath
import com.justai.jaicf.plugin.scenarios.transition.statesOrSuggestions
import com.justai.jaicf.plugin.scenarios.transition.transitToState
import javax.swing.Icon
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtValueArgument

class StatePathLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        if (!isLeafIdentifier(element)) return

        val pathExpressions = element.pathExpressionsOfBoundedBlock

        pathExpressions.forEach {
            val expression = it.holderExpression
            val markerHolder =
                expression.findChildOfType<KtLiteralStringTemplateEntry>()?.asLeaf ?: expression.asLeaf
                ?: return@forEach
            val transitionResult = transitToState(it.bound, it.pathExpression)

            if (transitionResult.statesOrSuggestions().isEmpty())
                return@forEach

            transitionResult.statesOrSuggestions().onEach { state ->
                result.add(buildLineMarker(state.stateExpression, markerHolder))
            }.ifEmpty {
                result.add(buildLineMarker(null, markerHolder))
            }
        }
    }

    private fun buildLineMarker(expression: PsiElement?, markerHolderLeaf: LeafPsiElement) =
        NavigationGutterIconBuilder.create(GO_TO_STATES)
            .setAlignment(LEFT)
            .setTargets(listOfNotNull(expression))
            .setTooltipText("Navigate to state declaration")
            .setEmptyPopupText("No state declaration found")
            .createLineMarkerInfo(markerHolderLeaf)
}

class StateIdentifierLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        if (!isLeafIdentifier(element)) {
            return
        }

        val callExpression = element.getBoundedCallExpressionOrNull(KtValueArgument::class.java) ?: return
        if (!callExpression.isStateDeclaration) {
            return
        }

        buildLineMarker(callExpression, element as LeafPsiElement)?.let { result.add(it) }
    }

    private fun buildLineMarker(
        stateExpression: KtCallExpression,
        markerHolderLeaf: LeafPsiElement,
    ): RelatedItemLineMarkerInfo<PsiElement>? {
        val framingState = stateExpression.framingState
            ?: markerHolderLeaf.getBoundedCallExpressionOrNull(KtValueArgument::class.java)?.framingState
            ?: return null

        val usages = framingState.usages
            .mapNotNull { it.asLeaf }
            .mapNotNull {
                it.framingState ?: return@mapNotNull null
                it
            }
            .ifEmpty { return null }

        return NavigationGutterIconBuilder.create(Icons.STATE_USAGES)
            .setAlignment(LEFT)
            .setTargets(usages)
            .setTooltipText("Find references to this state")
            .setPopupTitle("Found References")
            .setCellRenderer(JumpExprCellRenderer)
            .createLineMarkerInfo(markerHolderLeaf)
    }
}

private object JumpExprCellRenderer : DefaultPsiElementCellRenderer() {

    override fun getElementText(element: PsiElement?): String {
        val framingState = element?.framingState ?: return super.getElementText(element)
        return "${framingState.absolutePath}"
    }

    override fun getContainerText(element: PsiElement?, name: String?): String? {
        if (element == null)
            return null

        val scenarioName =
            element.framingState?.scenario?.name
                ?: return super.getContainerText(element, name)

        return "defined in $scenarioName"
    }
}

private object Icons {
    val GO_TO_STATES: Icon = AllIcons.Gutter.WriteAccess
    val STATE_USAGES: Icon = AllIcons.Gutter.ReadAccess
}

private fun isLeafIdentifier(element: PsiElement) = element is LeafPsiElement && element.elementType == IDENTIFIER
