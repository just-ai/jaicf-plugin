package com.justai.jaicf.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.justai.jaicf.plugin.USES_REACTION_ANNOTATION_NAME
import com.justai.jaicf.plugin.USES_REACTION_METHOD_ARGUMENT_NAME
import com.justai.jaicf.plugin.argumentConstantValue
import com.justai.jaicf.plugin.declaration
import com.justai.jaicf.plugin.getMethodAnnotations
import com.justai.jaicf.plugin.reactionsClassFqName
import com.justai.jaicf.plugin.services.VersionService
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.actions.generate.findDeclaredFunction
import org.jetbrains.kotlin.idea.core.isOverridable
import org.jetbrains.kotlin.idea.debugger.sequence.psi.receiverType
import org.jetbrains.kotlin.idea.quickfix.classForRefactor
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlinx.serialization.compiler.resolve.toClassDescriptor

class UsesReactionUsageInspection : LocalInspectionTool() {

    override fun getID() = "UsesReactionUsageInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = UsesReactionUsageVisitor(holder)

    class UsesReactionUsageVisitor(holder: ProblemsHolder) : KtCallExpressionVisitor(holder) {

        override fun visitCallExpression(callExpression: KtCallExpression) {
            if (VersionService.isAnnotationsUnsupported(callExpression.project))
                return

            val receiverClass = callExpression.receiverType().toClassDescriptor ?: return
            if (!receiverClass.isFinal) return

            val annotations = callExpression.getMethodAnnotations(USES_REACTION_ANNOTATION_NAME)

            annotations.forEach { annotation ->
                val reactionName = annotation.argumentConstantValue(USES_REACTION_METHOD_ARGUMENT_NAME)

                if (reactionName == null) {
                    logger.warn("UsesReaction annotation has no reaction name. ${annotation.text}")
                    return@forEach
                }

                if (reactionIsNotOverridden(reactionName, callExpression)) {
                    registerWarning(
                        callExpression,
                        "$reactionName reaction is not implemented by this channel"
                    )
                }
            }
        }

        private fun reactionIsNotOverridden(reactionName: String, callExpression: KtCallExpression): Boolean {
            val reactionDescriptor = getReactionDescriptor(reactionName, callExpression) ?: return false

            return if (reactionDescriptor.isFinal) false
            else callExpression.receiverType()?.findOverridingFunction(reactionDescriptor) == null
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

    companion object {
        private val logger = Logger.getInstance(UsesReactionUsageInspection::class.java)
    }
}

class NotOverriddenReactionUsageInspection : LocalInspectionTool() {

    override fun getID() = "NotOverriddenReactionUsageInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = NotOverriddenReactionUsageVisitor(holder)

    class NotOverriddenReactionUsageVisitor(holder: ProblemsHolder) : KtCallExpressionVisitor(holder) {

        override fun visitCallExpression(callExpression: KtCallExpression) {
            if (!reactionIsNotOverridden(callExpression)) return

            val functionReceiverClass = callExpression.receiverType().toClassDescriptor ?: return

            if (!functionReceiverClass.isFinal) return

            val functionName = callExpression.declaration?.name

            if (functionName == null) {
                logger.warn("Function of reaction doesn't have a name. ${callExpression.text}")
                return
            }

            registerWarning(
                callExpression,
                "${functionName.capitalize()} reaction is not implemented by this channel"
            )
        }

        private fun reactionIsNotOverridden(callExpression: KtCallExpression): Boolean {
            val functionDeclaration = callExpression.declaration ?: return false

            val classOfFunctionDeclaration = functionDeclaration.fqName?.parent() ?: return false

            return classOfFunctionDeclaration == reactionsClassFqName && functionDeclaration.isOpenOrAbstract()
        }
    }

    companion object {
        private val logger = Logger.getInstance(NotOverriddenReactionUsageInspection::class.java)
    }
}

private fun KtFunction.isOpenOrAbstract() = isAbstract() || isOverridable()

private val MemberDescriptor.isFinal: Boolean
    get() = modality == Modality.FINAL

private fun KotlinType.findOverridingFunction(descriptor: FunctionDescriptor) = supertypes()
    .mapNotNull { it.toClassDescriptor }
    .firstOrNull { supertype ->
        supertype.findDeclaredFunction(
            descriptor.name.asString(),
            false
        ) { it.overriddenDescriptors.contains(descriptor) } != null
    }
