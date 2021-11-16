package com.justai.jaicf.plugin.providers

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.util.ProcessingContext
import com.justai.jaicf.plugin.scenarios.linker.framingState
import com.justai.jaicf.plugin.scenarios.transition.Lexeme
import com.justai.jaicf.plugin.scenarios.transition.StatePath
import com.justai.jaicf.plugin.scenarios.transition.plus
import com.justai.jaicf.plugin.scenarios.transition.statesOrSuggestions
import com.justai.jaicf.plugin.scenarios.transition.transit
import com.justai.jaicf.plugin.scenarios.transition.transitionsWithRanges
import com.justai.jaicf.plugin.utils.StatePathExpression.Joined
import com.justai.jaicf.plugin.utils.boundedPathExpression
import com.justai.jaicf.plugin.utils.isSimpleStringTemplate
import com.justai.jaicf.plugin.utils.rangeToEndOf
import com.justai.jaicf.plugin.utils.stringValueOrNull
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

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

        val statePathExpression = element.boundedPathExpression as? Joined ?: return emptyArray()
        val pathExpression = statePathExpression.declaration
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
    private val path: StatePath,
    textRange: TextRange = element.textRange,
) : PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {

    private val service = ReferenceContributorsAvailabilityService.getInstance(element)

    override fun resolve() =
        if (service?.available == true)
            element.framingState?.transit(path)?.statesOrSuggestions()?.singleOrNull()?.stateExpression
        else null

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        if (service?.available == true)
            element.framingState?.transit(path)?.statesOrSuggestions()
                ?.map { it.stateExpression }
                ?.map(::PsiElementResolveResult)?.toTypedArray()
                ?: emptyArray()
        else
            emptyArray()
}
