package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.justai.jaicf.plugin.utils.USES_REACTION_ANNOTATION_NAME
import com.justai.jaicf.plugin.utils.USES_REACTION_METHOD_ARGUMENT_NAME
import com.justai.jaicf.plugin.utils.argumentConstantValue
import com.justai.jaicf.plugin.utils.declaration
import com.justai.jaicf.plugin.utils.getMethodAnnotations
import com.justai.jaicf.plugin.utils.reactionsClassFqName
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.actions.generate.findDeclaredFunction
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.isOverridable
import org.jetbrains.kotlin.idea.debugger.sequence.psi.receiverType
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlinx.serialization.compiler.resolve.toClassDescriptor

class UsesReactionUsageInspection : LocalInspectionTool() {

    override fun getID() = "UsesReactionUsageInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = UsesReactionUsageVisitor(holder)

    class UsesReactionUsageVisitor(holder: ProblemsHolder) : KtCallExpressionVisitor(holder) {

        override fun visitCallExpression(callExpression: KtCallExpression) {
            val receiverClass = callExpression.receiverType().toClassDescriptor ?: return
            if (receiverClass.isOpenOrAbstract) return

            val annotations = callExpression.getMethodAnnotations(USES_REACTION_ANNOTATION_NAME)

            annotations.forEach { annotation ->
                val reactionName =
                    annotation.argumentConstantValue(USES_REACTION_METHOD_ARGUMENT_NAME) ?: return@forEach

                if (callExpression.reactionIsNotOverridden(reactionName)) {
                    registerWarning(
                        callExpression,
                        "$reactionName reaction is not implemented by this channel"
                    )
                }
            }
        }

        private fun KtCallExpression.reactionIsNotOverridden(reactionName: String): Boolean {
            val reactionDescriptor = getReactionDescriptor(reactionName, this) ?: return false

            return if (reactionDescriptor.isFinal) false
            else receiverType()?.findOverridingFunction(reactionDescriptor) == null
        }

        private fun getReactionDescriptor(reactionName: String, callExpression: KtCallExpression): FunctionDescriptor? {
            val reactionReceiver = callExpression.receiverType() ?: return null
            val reactionDeclaration = callExpression.declaration ?: return null

            val receiverSupertypes = reactionReceiver.supertypes().mapNotNull { it.toClassDescriptor }
            val receiverOfDeclaration = reactionDeclaration.receiverTypeReference?.classForRefactor()?.fqName

            val receiverDescriptor = receiverSupertypes.firstOrNull { it.fqNameOrNull() == receiverOfDeclaration }
            return receiverDescriptor?.findDeclaredFunction(reactionName, true) { true }
        }
    }
}

class NotOverriddenReactionUsageInspection : LocalInspectionTool() {

    override fun getID() = "NotOverriddenReactionUsageInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = NotOverriddenReactionUsageVisitor(holder)

    class NotOverriddenReactionUsageVisitor(holder: ProblemsHolder) : KtCallExpressionVisitor(holder) {

        override fun visitCallExpression(callExpression: KtCallExpression) {
            if (!callExpression.isNotOverriddenReaction) return

            val reactionReceiverClass = callExpression.receiverType().toClassDescriptor ?: return

            if (reactionReceiverClass.isOpenOrAbstract) return

            val reactionName = callExpression.declaration?.name ?: return

            registerWarning(
                callExpression,
                "$reactionName reaction is not implemented by this channel"
            )
        }

        private val KtCallExpression.isNotOverriddenReaction: Boolean
            get() {
                val functionDeclaration = declaration ?: return false
                val classOfFunctionDeclaration = functionDeclaration.fqName?.parent() ?: return false

                return classOfFunctionDeclaration == reactionsClassFqName && functionDeclaration.isOpenOrAbstract
            }
    }
}

private val KtFunction.isOpenOrAbstract get() = isAbstract() || isOverridable()

private val MemberDescriptor.isOpenOrAbstract: Boolean
    get() = !isFinal

private val MemberDescriptor.isFinal: Boolean
    get() = modality == Modality.FINAL

private fun KotlinType.findOverridingFunction(descriptor: FunctionDescriptor) = (supertypes() + this)
    .mapNotNull { it.toClassDescriptor }
    .firstOrNull { supertype ->
        supertype.findDeclaredFunction(
            descriptor.name.asString(),
            false
        ) { it.overriddenDescriptors.contains(descriptor) } != null
    }

private fun KtTypeReference.classForRefactor(): KtClass? {
    val bindingContext = analyze(BodyResolveMode.PARTIAL)
    val type = bindingContext[BindingContext.TYPE, this] ?: return null
    val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return null
    return DescriptorToSourceUtils.descriptorToDeclaration(classDescriptor) as? KtClass
}
