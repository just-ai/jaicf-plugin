package com.justai.jaicf.plugin.providers

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.SkipAutopopupInStrings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import com.justai.jaicf.plugin.scenarios.linker.allStates
import com.justai.jaicf.plugin.scenarios.linker.framingState
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.nameWithoutLeadSlashes
import com.justai.jaicf.plugin.scenarios.transition.Lexeme
import com.justai.jaicf.plugin.scenarios.transition.StatePath
import com.justai.jaicf.plugin.scenarios.transition.parent
import com.justai.jaicf.plugin.scenarios.transition.statesOrSuggestions
import com.justai.jaicf.plugin.scenarios.transition.transit
import com.justai.jaicf.plugin.utils.StatePathExpression.Joined
import com.justai.jaicf.plugin.utils.boundedPathExpression
import com.justai.jaicf.plugin.utils.isComplexStringTemplate
import com.justai.jaicf.plugin.utils.stringValueOrNull
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

class StatePathCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), StatePathCompletionProvider())
    }
}

class StatePathCompletionProvider : CompletionProvider<CompletionParameters>() {

    public override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        resultSet: CompletionResultSet,
    ) {
        val statePathExpression = parameters.position.boundedPathExpression as? Joined ?: return
        val pathExpression = statePathExpression.declaration
        val pathBeforeCaret = pathExpression.stringValueOrNull?.substringBeforeCaret() ?: return
        val statePath = StatePath.parse(pathBeforeCaret)
        val statesSuggestions: List<State> = getStatesSuggestions(pathExpression, statePath) ?: return

        if (isLastTransitionFitIntoElement(statePath, parameters)) {
            statesSuggestions
                .mapNotNull { it.nameWithoutLeadSlashes }
                .onEach {
                    resultSet
                        .withPrefixMatcherIfComplexExpression(pathExpression, statePath)
                        .addElement(LookupElementBuilder.create(it))
                }
                .ifNotEmpty { resultSet.stopHere() }
        } else {
            val prefix = statePath.lexemes.last().identifier

            statesSuggestions
                .asSequence()
                .mapNotNull { it.nameWithoutLeadSlashes }
                .filter { it.startsWith(prefix) }
                .map { it.substringAfter(prefix) }
                .filter { it.isNotBlank() }
                .onEach {
                    resultSet
                        .withPrefixMatcher("")
                        .addElement(LookupElementBuilder.create(it))
                }
                .toList()
                .ifNotEmpty { resultSet.stopHere() }
        }
    }

    private fun getStatesSuggestions(pathExpression: KtExpression, path: StatePath): List<State>? {
        val framingState = pathExpression.framingState ?: return null

        return framingState.transit(path.parent).statesOrSuggestions().flatMap { it.allStates }
    }

    private fun isLastTransitionFitIntoElement(path: StatePath, parameters: CompletionParameters) =
        !(
            path.lexemes.isNotEmpty() &&
                path.lexemes.last() is Lexeme.Transition &&
                path.lexemes.last().identifier.length > parameters.position.text.substringBeforeCaret().length
            )

    private fun String.substringBeforeCaret() = substringBefore("IntellijIdeaRulezzz")

    private fun CompletionResultSet.withPrefixMatcherIfComplexExpression(
        pathExpression: KtExpression,
        path: StatePath,
    ): CompletionResultSet {
        return if (pathExpression.isComplexStringTemplate)
            if (path.lexemes.last() == Lexeme.Slash)
                withPrefixMatcher("")
            else
                withPrefixMatcher(path.lexemes.last().identifier)
        else
            this
    }

    companion object {
        private val logger = Logger.getInstance(StatePathCompletionProvider::class.java)
    }
}

class StatePathAutoPopupHandler : TypedHandlerDelegate() {

    /**
     * Checks if editor caret is in context to suggest state completions
     * @return [TypedHandlerDelegate.Result.STOP] if we can try suggest state completions
     * */
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (charTyped !in supportedCharsForPopup) {
            return Result.CONTINUE
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return Result.CONTINUE

        if (element.boundedPathExpression != null) {
            AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null)
            return Result.STOP
        }
        return Result.CONTINUE
    }
}

class StatePathCompletionConfidenceProvider : CompletionConfidence() {

    /**
     * Skips completion if given [contextElement] is not valid state path
     * */
    override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        return if (SkipAutopopupInStrings.isInStringLiteral(contextElement)) {
            val charTyped = psiFile.text[offset - 1]
            if (charTyped !in supportedCharsForPopup) {
                return ThreeState.UNSURE
            }

            return if (contextElement.boundedPathExpression != null)
                ThreeState.NO
            else
                ThreeState.UNSURE
        } else
            ThreeState.UNSURE
    }
}

private val supportedCharsForPopup = listOf('"', '/')
