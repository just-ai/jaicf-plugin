package com.justai.jaicf.plugin.utils

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SlowOperations.allowSlowOperations
import java.lang.Integer.min
import org.jetbrains.kotlin.idea.debugger.sequence.psi.callName
import org.jetbrains.kotlin.idea.debugger.sequence.psi.receiverType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtBinaryExpression
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
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.AbbreviatedType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.utils.ifEmpty

fun KtCallElement.argumentConstantValue(identifier: String) =
    argumentExpression(identifier)?.stringValueOrNull

val KtExpression.stringValueOrNull: String?
    get() = ConstantResolver.getInstance(this)?.resolveExpression(this)

val KtExpression.isSimpleStringTemplate: Boolean
    get() = this is KtStringTemplateExpression && children.size <= 1

val KtExpression.isComplexStringTemplate: Boolean
    get() = !isSimpleStringTemplate

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

val KtFunction.source: KtFunction?
    get() = allowSlowOperations<KtFunction?, Throwable> {
        if (canNavigateToSource()) navigationElement as? KtFunction else null
    }

val KtFunction.receiverName get() = fqName?.parent()?.asString()

val KtFunction.parametersTypes get() = valueParameters.map { it.type()?.fqName?.asString() }

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
    get() = allowSlowOperations<KtFunction?, Throwable> {
        if (isExist) referenceExpression?.resolveToSource else null
    }

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

inline fun <reified T : PsiElement> PsiElement.findChildrenOfType(): Collection<T> {
    return PsiTreeUtil.findChildrenOfType(this, T::class.java)
}

fun KtCallExpression.isOverride(receiver: FqName, funName: String, parameters: List<String>? = null) =
    try {
        isExist && callName() == funName
            && isReceiverInheritedOf(receiver)
            && (parameters?.let { it == parametersTypes } ?: true)
    } catch (e: NullPointerException) {
        false
    }

fun KtCallExpression.isReceiverInheritedOf(baseClass: FqName): Boolean = allowSlowOperations<Boolean, Throwable> {
    receiverFqName == baseClass || receiverType()?.supertypes()?.any { it.fqName == baseClass } ?: false
}

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

/**
 * Returns first parent of type [KtLambdaArgument] within this [PsiElement] or null.
 *
 * @return null if we stopped by finding any of [stopAt] classes or parent stack exceeded.
 * */
fun PsiElement.getBoundedLambdaArgumentOrNull(vararg stopAt: Class<out PsiElement>) =
    getParentOfType<KtLambdaArgument>(true, *stopAt)

fun PsiElement.getFirstBoundedElement(
    targetTypes: List<Class<out PsiElement>>,
    allowedTypes: List<Class<out PsiElement>>? = null
): PsiElement? {
    var currentParent = parent
    while (currentParent != null) {
        if (currentParent.javaClass in targetTypes) return currentParent

        if (allowedTypes != null && currentParent.javaClass !in allowedTypes) return null

        currentParent = currentParent.parent
    }
    return null
}

val PsiElement.asLeaf: LeafPsiElement?
    get() = findChildOfType()

val KtValueArgument.identifier: String?
    get() = definedIdentifier ?: parameter()?.name

val KtDotQualifiedExpression.rootReceiverExpression: KtNameReferenceExpression?
    get() = when (val receiver = receiverExpression) {
        is KtDotQualifiedExpression -> receiver.rootReceiverExpression
        is KtNameReferenceExpression -> receiver
        else -> receiver.findChildOfType()
    }

val KtDotQualifiedExpression.edgeSelectorExpression: KtNameReferenceExpression?
    get() = when (val selector = selectorExpression) {
        is KtDotQualifiedExpression -> selector.edgeSelectorExpression
        is KtNameReferenceExpression -> selector
        else -> selector?.findChildOfType()
    }

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

val KtValueArgument.boundedCallExpressionOrNull
    get() = getParentOfType<KtCallExpression>(true)

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

val KtBinaryExpression.operands get() = left?.let { l -> right?.let { r -> listOf(l, r) } }

val KtBinaryExpression.isStringConcatenationExpression
    get() = (operationReference.resolve() as? KtFunction)?.let { declaration ->
        declaration.name == "plus" &&
            declaration.receiverName == "kotlin.String" &&
            declaration.parametersTypes.singleOrNull() == "kotlin.Any"
    } ?: false
