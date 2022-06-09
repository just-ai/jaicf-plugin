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
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result.CONTINUE
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result.STOP
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import com.intellij.util.ThreeState.NO
import com.intellij.util.ThreeState.UNSURE
import com.justai.jaicf.plugin.core.linker.allStates
import com.justai.jaicf.plugin.core.linker.framingState
import com.justai.jaicf.plugin.core.psi.dto.State
import com.justai.jaicf.plugin.core.psi.dto.nameWithoutLeadSlashes
import com.justai.jaicf.plugin.core.transition.Lexeme
import com.justai.jaicf.plugin.core.transition.StatePath
import com.justai.jaicf.plugin.core.transition.parent
import com.justai.jaicf.plugin.core.transition.statesOrSuggestions
import com.justai.jaicf.plugin.core.transition.transit
import com.justai.jaicf.plugin.utils.StatePathExpression.Joined
import com.justai.jaicf.plugin.utils.VersionService
import com.justai.jaicf.plugin.utils.boundedPathExpression
import com.justai.jaicf.plugin.utils.isComplexStringTemplate
import com.justai.jaicf.plugin.utils.isJaicfInclude
import com.justai.jaicf.plugin.utils.measure
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
        parameters.position.project.measure("StatePathCompletionProvider.addCompletions") {
            completions(parameters, resultSet)
        }
    }

    private fun completions(
        parameters: CompletionParameters,
        resultSet: CompletionResultSet
    ) {
        if (!VersionService.getInstance(parameters.originalFile.project).isJaicfInclude) return

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
}

class StatePathAutoPopupHandler : TypedHandlerDelegate() {

    /**
     * Checks if editor caret is in context to suggest state completions
     * @return [TypedHandlerDelegate.Result.STOP] if we can try suggest state completions
     * */
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile) =
        project.measure("StatePathAutoPopupHandler.checkAutoPopup") { result(project, charTyped, file, editor) }

    private fun result(
        project: Project,
        charTyped: Char,
        file: PsiFile,
        editor: Editor
    ): Result {
        if (!VersionService.getInstance(project).isJaicfInclude) return CONTINUE

        if (charTyped !in supportedCharsForPopup) {
            return CONTINUE
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return CONTINUE

        if (element.boundedPathExpression != null) {
            AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null)
            return STOP
        }
        return CONTINUE
    }
}

class StatePathCompletionConfidenceProvider : CompletionConfidence() {

    /**
     * Skips completion if given [contextElement] is not valid state path
     * */
    override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int) =
        contextElement.measure("StatePathCompletionConfidenceProvider.shouldSkipAutopopup(${contextElement.text})") {
            threeState(psiFile, contextElement, offset)
        }

    private fun threeState(
        psiFile: PsiFile,
        contextElement: PsiElement,
        offset: Int
    ): ThreeState {
        if (!VersionService.getInstance(psiFile.project).isJaicfInclude) return UNSURE

        return if (SkipAutopopupInStrings.isInStringLiteral(contextElement)) {
            val charTyped = psiFile.text[offset - 1]
            if (charTyped !in supportedCharsForPopup) {
                return UNSURE
            }

            return if (contextElement.boundedPathExpression != null)
                NO
            else
                UNSURE
        } else
            UNSURE
    }
}

private val supportedCharsForPopup = listOf('"', '/')
