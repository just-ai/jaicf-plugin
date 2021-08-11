package com.justai.jaicf.plugin.providers

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import com.justai.jaicf.plugin.Lexeme
import com.justai.jaicf.plugin.StatePath
import com.justai.jaicf.plugin.firstStateOrSuggestion
import com.justai.jaicf.plugin.getBoundedPathExpression
import com.justai.jaicf.plugin.getFramingState
import com.justai.jaicf.plugin.identifierReference
import com.justai.jaicf.plugin.plus
import com.justai.jaicf.plugin.rangeToEndOf
import com.justai.jaicf.plugin.stringValueOrNull
import com.justai.jaicf.plugin.transit
import com.justai.jaicf.plugin.transitionsWithRanges

class StatePathReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            StatePathReferenceProvider()
        )
    }
}

class StatePathReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val pathExpression = element.getBoundedPathExpression() ?: return emptyArray()
        val statePath = pathExpression.stringValueOrNull?.let { StatePath.parse(it) } ?: return emptyArray()

        return if (pathExpression.isSimpleStringTemplate) {
            var accPath = StatePath()

            statePath.transitionsWithRanges()
                .mapNotNull { (transition, range) ->
                    accPath += transition
                    if (transition is Lexeme.Transition.Root)
                        null
                    else
                        StatePsiReference(element, accPath, range.shiftRight(1))
                }.toTypedArray()
        } else {
            arrayOf(StatePsiReference(element, statePath, element.rangeToEndOf(pathExpression)))
        }
    }
}

class StatePsiReference(
    element: PsiElement,
    path: StatePath,
    textRange: TextRange = element.textRange
) : PsiReferenceBase<PsiElement?>(element, textRange) {

    private val transitionResult by lazy { element.getFramingState()?.transit(path) }

    override fun resolve() = transitionResult?.firstStateOrSuggestion()?.identifierReference
}
