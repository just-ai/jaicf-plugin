package com.justai.jaicf.plugin.providers

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.justai.jaicf.plugin.TransitionResult.SuggestionsFound
import com.justai.jaicf.plugin.absolutePath
import com.justai.jaicf.plugin.asLeaf
import com.justai.jaicf.plugin.findChildOfType
import com.justai.jaicf.plugin.getBoundedCallExpressionOrNull
import com.justai.jaicf.plugin.getFramingState
import com.justai.jaicf.plugin.isValid
import com.justai.jaicf.plugin.pathExpressionsOfBoundedBlock
import com.justai.jaicf.plugin.providers.Icons.MULTI_RECEIVER_ICON
import com.justai.jaicf.plugin.providers.Icons.NO_RECEIVER_ICON
import com.justai.jaicf.plugin.providers.Icons.SINGLE_RECEIVER_ICON
import com.justai.jaicf.plugin.providers.Icons.SUGGESTIONS_ICON
import com.justai.jaicf.plugin.services.StateUsagesSearchService
import com.justai.jaicf.plugin.services.isStateDeclaration
import com.justai.jaicf.plugin.services.name
import com.justai.jaicf.plugin.states
import com.justai.jaicf.plugin.statesOrSuggestions
import com.justai.jaicf.plugin.transitToState
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
            val markerHolder =
                it.findChildOfType<KtLiteralStringTemplateEntry>()?.asLeaf ?: it.asLeaf ?: return@forEach
            val transitionResult = transitToState(it)
            val icon = when {
                transitionResult.states().size > 1 -> MULTI_RECEIVER_ICON
                transitionResult.states().size == 1 -> SINGLE_RECEIVER_ICON
                transitionResult is SuggestionsFound -> SUGGESTIONS_ICON
                else -> NO_RECEIVER_ICON
            }

            transitionResult.statesOrSuggestions().onEach { state ->
                if (state.isValid)
                    result.add(buildLineMarker(state.callExpression, markerHolder, icon))
                else
                    logger.warn("Transited state is invalid. $state")
            }.ifEmpty {
                result.add(buildLineMarker(null, markerHolder, icon))
            }
        }
    }

    private fun buildLineMarker(expression: PsiElement?, markerHolderLeaf: LeafPsiElement, icon: Icon) =
        NavigationGutterIconBuilder.create(icon)
            .setAlignment(Alignment.LEFT)
            .setTargets(listOfNotNull(expression))
            .setTooltipText("Navigate to state declaration")
            .setEmptyPopupText("No state declaration found")
            .createLineMarkerInfo(markerHolderLeaf)

    companion object {
        private val logger = Logger.getInstance(StatePathLineMarkerProvider::class.java)
    }
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
        val framingState = stateExpression.getFramingState()
            ?: markerHolderLeaf.getBoundedCallExpressionOrNull(KtValueArgument::class.java)?.getFramingState()
            ?: return null

        val service = StateUsagesSearchService[stateExpression] ?: return null
        val usages = service.findStateUsages(framingState)
            .mapNotNull { it.asLeaf }
            .mapNotNull {
                it.getFramingState() ?: return@mapNotNull null
                it
            }
            .ifEmpty { return null }

        return NavigationGutterIconBuilder.create(Icons.SENDER)
            .setAlignment(LEFT)
            .setTargets(usages)
            .setTooltipText("Find references to this state")
            .setPopupTitle("Found References")
            .setCellRenderer(JumpExprCellRenderer)
            .createLineMarkerInfo(markerHolderLeaf)
    }

    companion object {
        private val logger = Logger.getInstance(StateIdentifierLineMarkerProvider::class.java)
    }
}

private object JumpExprCellRenderer : DefaultPsiElementCellRenderer() {

    override fun getElementText(element: PsiElement?): String {
        val framingState = element?.getFramingState() ?: return super.getElementText(element)
        return "${framingState.absolutePath}"
    }

    override fun getContainerText(element: PsiElement?, name: String?): String? {
        if (element == null)
            return null

        val scenarioName =
            element.getFramingState()?.scenario?.name
                ?: return super.getContainerText(element, name)

        return "defined in $scenarioName"
    }
}

private object Icons {
    val SINGLE_RECEIVER_ICON: Icon = AllIcons.RunConfigurations.TestState.Run
    val MULTI_RECEIVER_ICON: Icon = AllIcons.RunConfigurations.TestState.Run_run
    val SUGGESTIONS_ICON: Icon = AllIcons.RunConfigurations.TestState.Yellow2
    val NO_RECEIVER_ICON: Icon = AllIcons.RunConfigurations.TestState.Red2
    val SENDER: Icon = AllIcons.Gutter.ReadAccess
}

private fun isLeafIdentifier(element: PsiElement) = element is LeafPsiElement && element.elementType == IDENTIFIER
