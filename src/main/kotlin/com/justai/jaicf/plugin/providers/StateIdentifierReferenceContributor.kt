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
import com.justai.jaicf.plugin.utils.STATE_NAME_ARGUMENT_NAME
import com.justai.jaicf.plugin.getBoundedCallExpressionOrNull
import com.justai.jaicf.plugin.getBoundedValueArgumentOrNull
import com.justai.jaicf.plugin.identifier
import com.justai.jaicf.plugin.rangeToEndOf
import com.justai.jaicf.plugin.scenarios.linter.usages
import com.justai.jaicf.plugin.scenarios.locator.framingState
import com.justai.jaicf.plugin.scenarios.psi.builders.isStateDeclaration
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

        val argument = element.getBoundedValueArgumentOrNull() ?: return emptyArray()

        if (!argument.isNameOfStateDeclaration) {
            return emptyArray()
        }

        return arrayOf(
            MultiPsiReference(element, element.rangeToEndOf(argument)) {
                argument.framingState?.usages
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

    private val service = ReferenceContributorsAvailabilityService.getInstance(element)

    override fun resolve() =
        if (service?.available == true)
            referencesProvider().singleOrNull()
        else null

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        return if (service?.available == true)
            referencesProvider().map(::PsiElementResolveResult).toTypedArray()
        else emptyArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        return super.handleElementRename(newElementName)
    }
}

val KtValueArgument.isNameOfStateDeclaration: Boolean
    get() = getBoundedCallExpressionOrNull()?.isStateDeclaration == true && identifier == STATE_NAME_ARGUMENT_NAME
