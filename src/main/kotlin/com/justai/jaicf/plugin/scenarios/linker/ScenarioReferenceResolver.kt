package com.justai.jaicf.plugin.scenarios.linker

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker
import com.justai.jaicf.plugin.scenarios.JaicfService
import com.justai.jaicf.plugin.scenarios.psi.ScenarioDataService
import com.justai.jaicf.plugin.scenarios.psi.builders.isStateDeclaration
import com.justai.jaicf.plugin.scenarios.psi.dto.Append
import com.justai.jaicf.plugin.scenarios.psi.dto.Scenario
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.TopLevelAppend
import com.justai.jaicf.plugin.utils.SCENARIO_MODEL_FIELD_NAME
import com.justai.jaicf.plugin.utils.argumentExpressionOrDefaultValue
import com.justai.jaicf.plugin.utils.isExist
import com.justai.jaicf.plugin.utils.isRemoved
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
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

    private val resolvedReferences by cached(PsiModificationTracker.MODIFICATION_COUNT) {
        mutableMapOf<KtReferenceExpression, Scenario?>()
    }

    fun resolve(scenarioReference: KtReferenceExpression, boundedState: State? = null): Scenario? {
        if (scenarioReference.isRemoved || !enabled) return null

        resolvedReferences?.get(scenarioReference)?.let { return it }

        val body = getScenarioBody(scenarioReference, boundedState) ?: return null
        return resolveScenario(body)?.also {
            resolvedReferences?.set(scenarioReference, it)
        }
    }

    private fun resolveScenario(scenarioBody: KtExpression): Scenario? {
        val file = scenarioBody.containingFile as? KtFile ?: return null
        return scenarioService.getScenarios(file)?.findBoundingScenario(scenarioBody)
    }

    private fun getScenarioBody(scenarioReference: KtReferenceExpression, boundedState: State? = null): KtExpression? {
        when (val resolvedElement = scenarioReference.resolve()) {
            is KtObjectDeclaration -> {
                return resolvedElement.scenarioBody
            }

            is KtProperty -> {
                return resolvedElement.delegateExpressionOrInitializer ?: resolvedElement.getter?.initializer
            }

            is KtParameter -> {
                val parameterName = resolvedElement.name ?: return null
                // TODO Maybe make a better search for value of parameter
                val argumentExpression =
                    boundedState?.stateExpression?.argumentExpressionOrDefaultValue(parameterName) as? KtReferenceExpression
                return argumentExpression?.let { getScenarioBody(it) }
            }

            is KtCallExpression -> {
                return if (resolvedElement.isStateDeclaration) scenarioReference else null
            }

            else -> {
                if (scenarioReference !is KtCallExpression) return null

                if (scenarioReference.isStateDeclaration) return scenarioReference

                return when (val resolved = scenarioReference.referenceExpression()?.resolve()) {
                    is KtNamedFunction -> (resolved.initializer as? KtCallExpression)?.let {
                        if (it.isStateDeclaration) it else null
                    }

                    is KtClass -> resolved.body?.scenarioBody

                    is KtPrimaryConstructor ->
                        scenarioReference.argumentExpressionOrDefaultValue(SCENARIO_MODEL_FIELD_NAME)

                    else -> null
                }
            }
        }
    }

    private val KtDeclarationContainer.scenarioBody: KtExpression?
        get() = declarations
            .filter { it.name == SCENARIO_MODEL_FIELD_NAME && it is KtProperty }
            .map { it as KtProperty }
            .mapNotNull { it.delegateExpressionOrInitializer ?: it.getter?.initializer }
            .firstOrNull()

    companion object {
        fun getInstance(element: PsiElement): ScenarioReferenceResolver? =
            if (element.isExist) getInstance(element.project)
            else null

        fun getInstance(project: Project): ScenarioReferenceResolver =
            project.getService(ScenarioReferenceResolver::class.java)
    }
}

val Append.scenario
    get() = ScenarioReferenceResolver.getInstance(project).resolve(this.referenceToScenario, parentState)

val TopLevelAppend.scenario
    get() = this.referenceToScenario?.let { ScenarioReferenceResolver.getInstance(project).resolve(it) }
