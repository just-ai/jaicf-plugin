package com.justai.jaicf.plugin.scenarios.linter

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker
import com.justai.jaicf.plugin.utils.SCENARIO_MODEL_FIELD_NAME
import com.justai.jaicf.plugin.argumentExpressionOrDefaultValue
import com.justai.jaicf.plugin.findChildOfType
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.isRemoved
import com.justai.jaicf.plugin.scenarios.JaicfService
import com.justai.jaicf.plugin.scenarios.psi.ScenarioDataService
import com.justai.jaicf.plugin.scenarios.psi.dto.Append
import com.justai.jaicf.plugin.scenarios.psi.dto.Scenario
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.TopLevelAppend
import com.justai.jaicf.plugin.scenarios.psi.dto.contains
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class ScenarioReferenceResolver(project: Project) : JaicfService(project) {

    private val scenarioService = ScenarioDataService[project]

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
        return scenarioService.getScenarios(file)?.firstOrNull { contains(it.innerState, scenarioBody) }
    }

    private fun getScenarioBody(scenarioReference: KtReferenceExpression, boundedState: State? = null): KtExpression? {
        when (val resolvedElement = scenarioReference.resolve()) {
            is KtObjectDeclaration ->
                return resolvedElement.declarations
                    .filter { it.name == SCENARIO_MODEL_FIELD_NAME && it is KtProperty }
                    .map { it as KtProperty }
                    .mapNotNull { it.initializer ?: it.delegateExpression }
                    .firstOrNull()

            is KtProperty -> return resolvedElement.initializer

            is KtParameter -> {
                val parameterName = resolvedElement.name ?: return null
                // TODO Maybe make a better search for value of parameter
                val argumentExpression =
                    boundedState?.stateExpression?.argumentExpressionOrDefaultValue(parameterName) as? KtReferenceExpression
                return argumentExpression?.let { getScenarioBody(it) }
            }

            else -> {
                return if (scenarioReference is KtCallExpression) {
                    val constructor =
                        scenarioReference.findChildOfType<KtReferenceExpression>()?.resolve() ?: return null
                    val ktClass = constructor.getParentOfType<KtClass>(true) ?: return null
                    val body = ktClass.findChildOfType<KtClassBody>() ?: return null

                    body.declarations
                        .filter { it.name == SCENARIO_MODEL_FIELD_NAME && it is KtProperty }
                        .mapNotNull { (it as KtProperty).initializer }
                        .firstOrNull()
                } else {
                    null
                }
            }
        }
    }

    companion object {
        operator fun get(element: PsiElement): ScenarioReferenceResolver? =
            if (element.isExist) get(element.project)
            else null

        operator fun get(project: Project): ScenarioReferenceResolver =
            project.getService(ScenarioReferenceResolver::class.java)
    }
}

val Append.scenario
    get() = ScenarioReferenceResolver[project].resolve(this.referenceToScenario, parentState)

val TopLevelAppend.scenario
    get() = this.referenceToScenario?.let { ScenarioReferenceResolver[project].resolve(it) }