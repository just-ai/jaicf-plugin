package com.justai.jaicf.plugin.providers

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.justai.jaicf.plugin.State
import com.justai.jaicf.plugin.absolutePath
import com.justai.jaicf.plugin.asLeaf
import com.justai.jaicf.plugin.findChildOfType
import com.justai.jaicf.plugin.getBoundedCallExpressionOrNull
import com.justai.jaicf.plugin.getFramingState
import com.justai.jaicf.plugin.getPathExpressionsOfBoundedBlock
import com.justai.jaicf.plugin.identifierReference
import com.justai.jaicf.plugin.isValid
import com.justai.jaicf.plugin.services.ScenarioService
import com.justai.jaicf.plugin.services.UsagesSearchService
import com.justai.jaicf.plugin.services.isStateDeclaration
import com.justai.jaicf.plugin.statesOrSuggestions
import com.justai.jaicf.plugin.transitToState
import javax.swing.Icon
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtValueArgument
import java.util.Collections.emptyList

class StatePathLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        if (!isLeafIdentifier(element)) {
            return
        }

        val pathExpressions: List<KtExpression> = element.getPathExpressionsOfBoundedBlock()

        pathExpressions.forEach {
            val markerHolder =
                it.findChildOfType<KtLiteralStringTemplateEntry>()?.asLeaf ?: it.asLeaf ?: return@forEach

            transitToState(it).statesOrSuggestions().forEach { state ->
                if (state.isValid)
                    result.add(buildLineMarker(state, markerHolder))
                else
                    logger.warn("Transited state is invalid. $state")
            }
        }
    }

    private fun buildLineMarker(state: State, markerHolderLeaf: LeafPsiElement) =
        NavigationGutterIconBuilder.create(Icons.RECEIVER)
            .setAlignment(Alignment.LEFT)
            .setTargets(listOf(state.callExpression))
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

        result.add(buildLineMarker(callExpression, element as LeafPsiElement))
    }

    private fun buildLineMarker(stateExpression: KtCallExpression, markerHolderLeaf: LeafPsiElement) =
        NavigationGutterIconBuilder.create(Icons.SENDER)
            .setAlignment(Alignment.LEFT)
            .setTargets(
                NotNullLazyValue.createValue {
                    val framingState = stateExpression.getFramingState()
                        ?: markerHolderLeaf.getBoundedCallExpressionOrNull(KtValueArgument::class.java)
                            ?.getFramingState()

                    if (framingState == null) {
                        logger.warn("Framing state of stateExpression is null. ${stateExpression.text}")
                        return@createValue emptyList()
                    }

                    if (!framingState.isValid) {
                        logger.warn("Framing state is invalid. $framingState")
                        return@createValue emptyList()
                    }

                    UsagesSearchService.get(stateExpression.project).findStateUsages(framingState)
                        .mapNotNull { it.asLeaf }
                        .mapNotNull {
                            it.getFramingState() ?: return@mapNotNull null
                            it
                        }
                }
            )
            .setEmptyPopupText("No references to this state found")
            .setTooltipText("Find references to this state")
            .setPopupTitle("Found References")
            .setCellRenderer(JumpExprCellRenderer)
            .createLineMarkerInfo(markerHolderLeaf)

    companion object {
        private val logger = Logger.getInstance(StateIdentifierLineMarkerProvider::class.java)
    }
}

private object JumpExprCellRenderer : DefaultPsiElementCellRenderer() {

    override fun getElementText(element: PsiElement?): String {
        return element?.getFramingState()?.absolutePath()?.toString()
            ?: super.getElementText(element)
    }

    override fun getContainerText(element: PsiElement?, name: String?): String? {
        if (element == null)
            return null

        val reference =
            ServiceManager.getService(element.project, ScenarioService::class.java).getScenarioReference(element)
                ?: return super.getContainerText(element, name)

        return "defined in ${reference.name}"
    }
}

private object Icons {
    var RECEIVER: Icon = AllIcons.Gutter.WriteAccess
    var SENDER: Icon = AllIcons.Gutter.ReadAccess
}

private fun isLeafIdentifier(element: PsiElement) = element is LeafPsiElement && element.elementType == IDENTIFIER
