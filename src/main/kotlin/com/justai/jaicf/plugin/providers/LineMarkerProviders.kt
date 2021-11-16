package com.justai.jaicf.plugin.providers

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.justai.jaicf.plugin.providers.Icons.GO_TO_STATES
import com.justai.jaicf.plugin.scenarios.linker.framingState
import com.justai.jaicf.plugin.scenarios.linker.usages
import com.justai.jaicf.plugin.scenarios.psi.builders.isStateDeclaration
import com.justai.jaicf.plugin.scenarios.psi.dto.name
import com.justai.jaicf.plugin.scenarios.transition.absolutePath
import com.justai.jaicf.plugin.scenarios.transition.statesOrSuggestions
import com.justai.jaicf.plugin.scenarios.transition.transitToState
import com.justai.jaicf.plugin.utils.asLeaf
import com.justai.jaicf.plugin.utils.findChildOfType
import com.justai.jaicf.plugin.utils.getBoundedCallExpressionOrNull
import com.justai.jaicf.plugin.utils.holderExpression
import com.justai.jaicf.plugin.utils.isRemoved
import com.justai.jaicf.plugin.utils.pathExpressionsOfBoundedBlock
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
        if (element.isRemoved || isNotLeafIdentifier(element)) return

        val pathExpressions = element.pathExpressionsOfBoundedBlock

        pathExpressions.onEach {
            val expression = it.holderExpression
            val markerHolder =
                expression.findChildOfType<KtLiteralStringTemplateEntry>()?.asLeaf ?: expression.asLeaf
                ?: return@onEach

            transitToState(it.usePoint, it.declaration).statesOrSuggestions().onEach { state ->
                result.add(buildLineMarker(state.stateExpression, markerHolder))
            }
        }
    }

    private fun buildLineMarker(
        expression: PsiElement,
        markerHolderLeaf: LeafPsiElement
    ): RelatedItemLineMarkerInfo<PsiElement> =
        NavigationGutterIconBuilder.create(GO_TO_STATES)
            .setAlignment(LEFT)
            .setTargets(listOf(expression))
            .setTooltipText("Navigate to state declaration")
            .setEmptyPopupText("No state declaration found")
            .createLineMarkerInfo(markerHolderLeaf)
}

class StateIdentifierLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        if (element.isRemoved || isNotLeafIdentifier(element)) return

        val callExpression = element.getBoundedCallExpressionOrNull(KtValueArgument::class.java) ?: return
        if (!callExpression.isStateDeclaration) return

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
            .mapNotNull { it.holderExpression.asLeaf }
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
    val GO_TO_STATES: Icon = AllIcons.Actions.ArrowExpand
    val STATE_USAGES: Icon = AllIcons.Hierarchy.Supertypes
}

private fun isNotLeafIdentifier(element: PsiElement) = element !is LeafPsiElement || element.elementType != IDENTIFIER
