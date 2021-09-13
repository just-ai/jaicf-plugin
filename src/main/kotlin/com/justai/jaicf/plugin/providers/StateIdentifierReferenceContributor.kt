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
import com.justai.jaicf.plugin.STATE_NAME_ARGUMENT_NAME
import com.justai.jaicf.plugin.TransitionResult
import com.justai.jaicf.plugin.getBoundedCallExpressionOrNull
import com.justai.jaicf.plugin.getBoundedValueArgumentOrNull
import com.justai.jaicf.plugin.getFramingState
import com.justai.jaicf.plugin.identifier
import com.justai.jaicf.plugin.rangeToEndOf
import com.justai.jaicf.plugin.services.AvailabilityService
import com.justai.jaicf.plugin.services.StateUsagesSearchService
import com.justai.jaicf.plugin.services.isStateDeclaration
import com.justai.jaicf.plugin.transit
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

class StateIdentifierReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            StateIdentifierReferenceProvider()
        )
    }
}

class StateIdentifierReferenceProvider : PsiReferenceProvider() {

    @ExperimentalStdlibApi
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (AvailabilityService[element]?.referenceContributorAvailable == false)
            return emptyArray()

        val argument = element.getBoundedValueArgumentOrNull() ?: return emptyArray()

        if (!argument.isNameOfStateDeclaration) {
            return emptyArray()
        }

        return arrayOf(
            MultiPsiReference(element, element.rangeToEndOf(argument)) {
                argument.getFramingState()?.let { StateUsagesSearchService[element]?.findStateUsages(it) }
                    ?: emptyList()
            }
        )
    }
}

class MultiPsiReference(
    element: PsiElement,
    textRange: TextRange = element.textRange,
    private val referencesProvider: () -> List<PsiElement>,
) : PsiReferenceBase<PsiElement?>(element, textRange), PsiPolyVariantReference {

    private val references: List<PsiElement>
        get() {
            savedReferences?.let { return it }

            if (AvailabilityService[element]?.referenceContributorAvailable == false)
                return emptyList()

            return referencesProvider().also {
                savedReferences = it
            }
        }

    // TODO add caches to navigation
    private var savedReferences: List<PsiElement>? = null

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        return references.map(::PsiElementResolveResult).toTypedArray()
    }

    override fun resolve() = references.firstOrNull()
}

val KtValueArgument.isNameOfStateDeclaration: Boolean
    get() = getBoundedCallExpressionOrNull()?.isStateDeclaration == true && identifier == STATE_NAME_ARGUMENT_NAME
