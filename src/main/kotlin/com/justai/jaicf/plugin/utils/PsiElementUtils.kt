package com.justai.jaicf.plugin

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.debugger.sequence.psi.callName
import org.jetbrains.kotlin.idea.debugger.sequence.psi.receiverType
import org.jetbrains.kotlin.idea.inspections.AbstractPrimitiveRangeToInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.AbbreviatedType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.utils.ifEmpty
import java.lang.Integer.min

fun KtCallElement.argumentConstantValue(identifier: String) =
    argumentExpression(identifier)?.stringValueOrNull

val KtExpression.stringValueOrNull: String?
    get() = constantValueOrNull()?.value?.toString()

fun KtCallElement.argumentExpressionOrDefaultValue(identifier: String) =
    argumentExpression(identifier) ?: parameter(identifier)?.defaultValue

fun KtCallExpression.argumentExpression(parameter: KtParameter) =
    parameter.name?.let { argumentExpression(it) }

fun KtCallExpression.argumentExpressionsOrDefaultValues(parameter: KtParameter) =
    parameter.name?.let { argumentExpressions(it) }?.ifEmpty { listOfNotNull(parameter.defaultValue) }
        ?: listOfNotNull(parameter.defaultValue)

fun KtCallElement.argumentExpression(identifier: String) =
    valueArgument(identifier)?.getArgumentExpression()

fun KtCallElement.argumentExpressions(identifier: String) =
    valueArgumentOrVarargArguments(identifier).map { it.getArgumentExpression() }

fun KtCallElement.parameter(identifier: String) =
    declaration?.valueParameters?.firstOrNull { it.name == identifier }

fun KtCallExpression.argumentExpressionsByAnnotation(name: String) =
    parametersByAnnotation(name)
        .mapNotNull { argumentExpression(it) }

fun KtCallExpression.argumentExpressionsOrDefaultValuesByAnnotation(name: String) =
    parametersByAnnotation(name)
        .flatMap { argumentExpressionsOrDefaultValues(it) }.filterNotNull()

fun KtCallExpression.parametersByAnnotation(name: String) =
    parameters.filter { it.annotationNames.contains(name) }

fun KtCallExpression.getMethodAnnotations(name: String) =
    declaration?.getMethodAnnotations(name) ?: emptyList()

fun KtFunction.getMethodAnnotations(name: String) =
    annotationEntries.filter { it.shortName?.asString() == name }

val KtFunction.isBinary get() = isExist && containingFile.name.endsWith(".class")

val KtFunction.source get() = if (canNavigateToSource()) navigationElement as? KtFunction else null

fun KtCallElement.valueArgument(identifier: String) =
    valueArguments.firstOrNull { it is KtValueArgument && it.identifier == identifier }

fun KtCallElement.valueArgumentOrVarargArguments(identifier: String) =
    valueArguments.filter { it is KtValueArgument && it.identifier == identifier }

val KtCallExpression.parametersTypes: List<String>
    get() = declaration?.valueParameters?.map { it.type().toString() } ?: emptyList()

val KtCallExpression.parameters: List<KtParameter>
    get() = declaration?.valueParameters ?: emptyList()

val KtParameter.annotationNames: List<String>
    get() = annotationEntries.mapNotNull { it.shortName?.asString() }

val KtCallElement.declaration: KtFunction?
    get() = if (isExist) referenceExpression?.resolveToSource else null

val KtCallElement.referenceExpression: KtReferenceExpression?
    get() = findChildOfType<KtNameReferenceExpression>()

val KtReferenceExpression.resolveToSource: KtFunction?
    get() = (resolve() as? KtFunction)?.let {
        if (it.isBinary) it.source
        else it
    }

inline fun <reified T : PsiElement> PsiElement.findChildOfType(): T? {
    return PsiTreeUtil.findChildOfType(this, T::class.java)
}

fun KtCallExpression.isOverride(receiver: FqName, funName: String, parameters: List<String>? = null) =
    try {
        isExist && callName() == funName
                && isReceiverInheritedOf(receiver)
                && (parameters?.let { it == parametersTypes } ?: true)
    } catch (e: NullPointerException) {
        false
    }

fun KtCallExpression.isReceiverInheritedOf(baseClass: FqName) =
    receiverFqName == baseClass ||
            receiverType()?.supertypes()?.any { it.fqName == baseClass } ?: false

val KtCallExpression.receiverFqName: FqName?
    get() = receiverType()?.fqName ?: declaration?.fqName?.parent()

fun KtCallExpression.nameReferenceExpression() = getChildOfType<KtNameReferenceExpression>()

/**
 * Returns first parent of type [KtValueArgument] within this [PsiElement] or null
 *
 * @return null if we found [KtProperty] class or parent stack exceeded
 * */
fun PsiElement.getBoundedValueArgumentOrNull() =
    getParentOfType<KtValueArgument>(true, KtProperty::class.java)

/**
 * Returns first parent of type [KtCallExpression] within this [PsiElement] or null.
 * May be useful in finding a function with argument (`state(<NAME>)` or `go(<PATH>)`)
 *
 * @return null if we stopped by finding any of [stopAt] classes or parent stack exceeded.
 * */
fun PsiElement.getBoundedCallExpressionOrNull(vararg stopAt: Class<out PsiElement>) =
    getParentOfType<KtCallExpression>(true, *stopAt)

fun PsiElement.getFirstBoundedElement(vararg elements: Class<out PsiElement>): PsiElement? {
    var currentParent = parent
    while (currentParent != null) {
        if (currentParent.javaClass in elements)
            return currentParent

        currentParent = currentParent.parent
    }
    return null
}

val PsiElement.asLeaf: LeafPsiElement?
    get() = findChildOfType()

val KtValueArgument.identifier: String?
    get() = definedIdentifier ?: parameter()?.name

fun KtDotQualifiedExpression.getRootDotReceiver(): KtNameReferenceExpression? =
    when (val receiver = getDotReceiver()) {
        is KtDotQualifiedExpression -> receiver.getRootDotReceiver()
        is KtNameReferenceExpression -> receiver
        else -> receiver.findChildOfType()
    }

fun KtDotQualifiedExpression.getDotReceiver(): PsiElement = children[0]

fun KtValueArgument.parameter(): KtParameter? {
    val callElement = getParentOfType<KtCallElement>(true) ?: return null
    val params = callElement.declaration?.valueParameters ?: return null

    if (params.isEmpty())
        return null

    if (this is KtLambdaArgument)
        return params.last()

    val identifier = definedIdentifier
    if (identifier != null)
        return params.firstOrNull { it.name == identifier }

    val indexOfArgument = callElement.valueArguments.indexOf(this)
    return params[min(indexOfArgument, params.lastIndex)]
}

val KtValueArgument.definedIdentifier: String?
    get() = getArgumentName()?.asName?.identifier

fun KtValueArgument.getBoundedCallExpressionOrNull() = getParentOfType<KtCallExpression>(true)

val PsiElement.isRemoved: Boolean
    get() = !this.isValid || containingFile == null

val PsiElement.isExist: Boolean
    get() = !isRemoved

val KotlinType.fqName: FqName?
    get() = when (this) {
        is AbbreviatedType -> abbreviation.fqName
        else -> constructor.declarationDescriptor?.fqNameOrNull()
    }

fun PsiElement.rangeToEndOf(parent: PsiElement): TextRange {
    if (this === parent) return TextRange(0, textLength)
    return TextRange(0, parent.textLength - textRangeInParent.startOffset)
}
