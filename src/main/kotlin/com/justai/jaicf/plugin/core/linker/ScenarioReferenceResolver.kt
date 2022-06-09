package com.justai.jaicf.plugin.core.linker

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker
import com.justai.jaicf.plugin.core.JaicfService
import com.justai.jaicf.plugin.core.psi.ScenarioDataService
import com.justai.jaicf.plugin.core.psi.builders.isStateDeclaration
import com.justai.jaicf.plugin.core.psi.dto.Append
import com.justai.jaicf.plugin.core.psi.dto.NestedAppend
import com.justai.jaicf.plugin.core.psi.dto.Scenario
import com.justai.jaicf.plugin.core.psi.dto.State
import com.justai.jaicf.plugin.utils.SCENARIO_MODEL_FIELD_NAME
import com.justai.jaicf.plugin.utils.argumentExpressionOrDefaultValue
import com.justai.jaicf.plugin.utils.isExist
import com.justai.jaicf.plugin.utils.isRemoved
import com.justai.jaicf.plugin.utils.measure
import com.justai.jaicf.plugin.utils.safeResolve
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclarationContainer
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

class ScenarioReferenceResolver(project: Project) : JaicfService(project) {

    private val scenarioService = ScenarioDataService.getInstance(project)

    // TODO optimize
    private val resolvedReferences by cached(PsiModificationTracker.MODIFICATION_COUNT) {
        mutableMapOf<Pair<KtReferenceExpression, State?>, Scenario?>()
    }

    fun resolve(scenarioReference: KtReferenceExpression, boundedState: State? = null) =
        measure("ScenarioReferenceResolver.resolve(${scenarioReference.text})") {
            tempResolve(scenarioReference, boundedState)
        }

    private fun tempResolve(scenarioReference: KtReferenceExpression, boundedState: State? = null): Scenario? {
        if (scenarioReference.isRemoved || !enabled) return null

        resolvedReferences?.get(scenarioReference to boundedState)?.let { return it }

        val body = getScenarioBody(scenarioReference, boundedState) ?: return null
        return resolveScenario(body)?.also {
            resolvedReferences?.set(scenarioReference to boundedState, it)
        }
    }

    private fun resolveScenario(scenarioBody: KtExpression): Scenario? =
        measure("ScenarioReferenceResolver.resolveScenario(...)") {
            val file = scenarioBody.containingFile as? KtFile ?: return@measure null
            scenarioService.getScenarios(file)?.findBoundingScenario(scenarioBody)
        }

    private fun getScenarioBody(scenarioReference: KtReferenceExpression, boundedState: State? = null): KtExpression? {
        when (val resolvedElement = scenarioReference.safeResolve()) {
            is KtObjectDeclaration -> {
                return resolvedElement.scenarioBody
            }

            is KtProperty -> {
                return resolvedElement.delegateExpressionOrInitializer ?: resolvedElement.getter?.initializer
            }

            is KtParameter -> {
                (resolvedElement.defaultValue as? KtReferenceExpression)?.let {
                    return getScenarioBody(it)
                }

                val parameterName = resolvedElement.name ?: return null
                val argumentExpression =
                    boundedState?.stateExpression?.argumentExpressionOrDefaultValue(parameterName) as? KtReferenceExpression

                return argumentExpression?.let { getScenarioBody(it) }
            }

            is KtCallExpression -> {
                return if (resolvedElement.isStateDeclaration) scenarioReference else null
            }

            else -> {
                return (scenarioReference as? KtCallExpression)?.let(::getScenarioBody)
            }
        }
    }

    private fun getScenarioBody(callExpression: KtCallExpression): KtExpression? {
        if (callExpression.isStateDeclaration) return callExpression

        return when (val resolved = callExpression.referenceExpression()?.safeResolve()) {
            is KtNamedFunction -> (resolved.initializer as? KtCallExpression)?.let {
                if (it.isStateDeclaration) it else null
            }

            is KtClass -> resolved.body?.scenarioBody

            is KtPrimaryConstructor ->
                callExpression.argumentExpressionOrDefaultValue(SCENARIO_MODEL_FIELD_NAME)

            else -> null
        }
    }

    private val KtDeclarationContainer.scenarioBody: KtExpression?
        get() = measure("KtDeclarationContainer.scenarioBody") {
            declarations
                .filter { it.name == SCENARIO_MODEL_FIELD_NAME && it is KtProperty }
                .map { it as KtProperty }
                .mapNotNull { it.delegateExpressionOrInitializer ?: it.getter?.initializer }
                .firstOrNull()
        }

    companion object {
        fun getInstance(element: PsiElement): ScenarioReferenceResolver? =
            if (element.isExist) getInstance(element.project)
            else null

        fun getInstance(project: Project): ScenarioReferenceResolver =
            project.getService(ScenarioReferenceResolver::class.java)
    }
}

val NestedAppend.scenario
    get() = ScenarioReferenceResolver.getInstance(project).resolve(this.referenceToScenario, parentState)

val Append.scenario
    get() = this.referenceToScenario?.let { ScenarioReferenceResolver.getInstance(project).resolve(it) }
