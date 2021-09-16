package com.justai.jaicf.plugin.services.linter

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker
import com.justai.jaicf.plugin.SCENARIO_MODEL_FIELD_NAME
import com.justai.jaicf.plugin.argumentExpressionOrDefaultValue
import com.justai.jaicf.plugin.findChildOfType
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.isRemoved
import com.justai.jaicf.plugin.services.Service
import com.justai.jaicf.plugin.services.managers.ScenarioDataManager
import com.justai.jaicf.plugin.services.managers.dto.Append
import com.justai.jaicf.plugin.services.managers.dto.Scenario
import com.justai.jaicf.plugin.services.managers.dto.State
import com.justai.jaicf.plugin.services.managers.dto.TopLevelAppend
import com.justai.jaicf.plugin.services.managers.dto.contains
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

class ScenarioReferenceResolver(project: Project) : Service(project) {

    private val appendWithResolve by cachedIfEnabled(PsiModificationTracker.MODIFICATION_COUNT) {
        println("ScenarioReferenceResolver: appendWithResolve updating")

        mutableMapOf<KtReferenceExpression, Scenario?>()
    }
    private val scenarioManager = ScenarioDataManager[project]

    fun resolve(reference: KtReferenceExpression, state: State? = null): Scenario? {
        if (reference.isRemoved) return null

        appendWithResolve?.get(reference)?.let { return it }

        val resolvedReference = reference.resolve()
        return if (resolvedReference is KtParameter) {
            val parameterName = resolvedReference.name ?: return null
            // TODO Maybe make a better search for value of parameter
            val expression =
                state?.stateExpression?.argumentExpressionOrDefaultValue(parameterName) as? KtReferenceExpression
            expression?.let { resolveScenario(it) }
        } else {
            resolveScenario(reference)
        }?.also {
            appendWithResolve?.set(reference, it)
        }
    }

    private fun resolveScenario(expr: KtReferenceExpression) = getScenarioBody(expr)?.let { block ->
        val file = block.containingFile as? KtFile ?: return null
        scenarioManager.getScenarios(file)?.firstOrNull { contains(it.innerState, block) }
    }

    private fun getScenarioBody(expr: KtReferenceExpression): KtExpression? {
        when (val resolvedElement = expr.resolve()) {
            is KtObjectDeclaration ->
                return resolvedElement.declarations
                    .filter { it.name == SCENARIO_MODEL_FIELD_NAME && it is KtProperty }
                    .map { it as KtProperty }
                    .mapNotNull { it.initializer ?: it.delegateExpression }
                    .firstOrNull()

            is KtProperty -> return resolvedElement.initializer

            else -> {
                return if (expr is KtCallExpression) {
                    val constructor = expr.findChildOfType<KtReferenceExpression>()?.resolve() ?: return null
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
