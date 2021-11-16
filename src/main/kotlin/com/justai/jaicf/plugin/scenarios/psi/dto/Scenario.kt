package com.justai.jaicf.plugin.scenarios.psi.dto

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.utils.CREATE_MODEL_METHOD_NAME
import com.justai.jaicf.plugin.utils.SCENARIO_METHOD_NAME
import com.justai.jaicf.plugin.utils.ScenarioPackageFqName
import com.justai.jaicf.plugin.utils.isOverride
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class Scenario(
    val project: Project,
    val file: KtFile,
) {
    lateinit var innerState: State

    val declarationElement: PsiElement
        get() = innerState.stateExpression

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Scenario

        if (file != other.file) return false
        if (innerState.stateExpression != other.innerState.stateExpression) return false

        return true
    }

    override fun hashCode(): Int {
        var result = file.hashCode()
        result = 31 * result + innerState.hashCode()
        return result
    }
}

val Scenario.nestedStates: List<State>
    get() = innerState.nestedStates + innerState

val Scenario.name: String?
    get() {
        val scenarioDeclaration = declarationElement as? KtCallExpression ?: return null
        val parent = scenarioDeclaration.parent ?: return null
        (parent as? KtNamedFunction)?.let { return it.name }

        val field = when (parent) {
            is KtProperty -> parent
            is KtPropertyAccessor -> parent.property
            is KtParameter -> parent
            else -> return null
        }
        return when {
            scenarioDeclaration.isScenario -> field.name
            scenarioDeclaration.isCreateModelFun -> field.getParentOfType<KtClassOrObject>(false)?.name
            else -> null
        }
    }

private val KtCallExpression.isScenario get() = isOverride(ScenarioPackageFqName, SCENARIO_METHOD_NAME)
private val KtCallExpression.isCreateModelFun get() = isOverride(ScenarioPackageFqName, CREATE_MODEL_METHOD_NAME)
