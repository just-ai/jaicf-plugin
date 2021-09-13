package com.justai.jaicf.plugin

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.services.AppendService
import com.justai.jaicf.plugin.services.ScenarioService
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtReferenceExpression

class Scenario(
    val file: KtFile,
    var condition: Condition = Condition.ACTUAL,
) {
    private val appendService by lazy { AppendService[file] }
    val topLevelAppends: List<Append>
        get() = appendService?.getAppends(this) ?: emptyList()

    lateinit var innerState: State

    val declarationElement: PsiElement
        get() = innerState.callExpression

    fun actual() {
        condition = Condition.ACTUAL
    }

    fun modified() {
        condition = Condition.MODIFIED
    }

    fun removed() {
        condition = Condition.REMOVED
    }

    fun corrupted() {
        condition = Condition.CORRUPTED
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Scenario

        if (file != other.file) return false
        if (innerState.callExpression != other.innerState.callExpression) return false

        return true
    }

    override fun hashCode(): Int {
        var result = file.hashCode()
        result = 31 * result + innerState.hashCode()
        return result
    }

    override fun toString() = "Scenario(condition=$condition, declarationElement=$declarationElement)"

    enum class Condition {
        ACTUAL, MODIFIED, REMOVED, CORRUPTED
    }
}

class State(
    val identifier: StateIdentifier,
    val parent: State?,
    val scenario: Scenario,
    val callExpression: KtCallExpression,
) {
    lateinit var appends: List<Append>
    lateinit var states: List<State>
    var textHashCode: Int = 0

    override fun toString() =
        "State(identifier=$identifier, scenario=$scenario, callExpression=${callExpression.text})"
}

val Scenario.isValid: Boolean
    get() = condition == Scenario.Condition.ACTUAL && declarationElement.isExist

val State.isValid: Boolean
    get() = scenario.isValid && callExpression.isExist && callExpression.text.hashCode() == textHashCode

val Scenario.actualCondition: Scenario.Condition
    get() {
        if (condition != Scenario.Condition.ACTUAL)
            return condition

        if (declarationElement.isRemoved) {
            return Scenario.Condition.REMOVED
        }

        val isActual = innerState.recursiveStateTraversal { state ->
            return@recursiveStateTraversal !state.callExpression.isRemoved &&
                    state.callExpression.text.hashCode() == state.textHashCode
        }

        return if (isActual) Scenario.Condition.ACTUAL
        else Scenario.Condition.MODIFIED
    }

private fun State.recursiveStateTraversal(action: (State) -> Boolean): Boolean {
    states.forEach {
        if (!action.invoke(it))
            return false

        if (!it.recursiveStateTraversal(action))
            return false
    }
    return true
}

sealed class StateIdentifier {

    abstract fun resolveText(): String?

    data class PredefinedIdentifier(
        val stateName: String,
        val container: PsiElement,
    ) : StateIdentifier() {
        override fun resolveText() = stateName
    }

    data class ExpressionIdentifier(
        val ktExpression: KtExpression,
        val valueArgumentElement: PsiElement,
    ) : StateIdentifier() {
        override fun resolveText() = ktExpression.stringValueOrNull
    }

    data class NoIdentifier(
        val parentCallExpression: KtCallExpression,
        val errorMessage: String = "",
    ) : StateIdentifier() {
        override fun resolveText(): String? = null
    }
}

val State.name get() = identifier.resolveText()

data class Append(
    val referenceToScenario: KtReferenceExpression,
    val callExpression: KtCallExpression,
    val parentState: State? = null,
)

class TopLevelAppend(
    val scenarioReceiver: Scenario,
    val callExpression: KtCallExpression,
    val dotExpression: KtDotQualifiedExpression,
) {
    val textHashCode: Int = dotExpression.text.hashCode()
    val file: KtFile = callExpression.containingKtFile

    private val referenceToScenario: KtReferenceExpression?
        get() = callExpression.argumentExpression(APPEND_OTHER_ARGUMENT_NAME) as? KtReferenceExpression

    val append: Append?
        get() = referenceToScenario?.let { Append(it, callExpression) }
}

val TopLevelAppend.isRemoved
    get() = dotExpression.isRemoved

val TopLevelAppend.isActual
    get() = textHashCode == dotExpression.text.hashCode()

fun Append.resolve(): Scenario? {
    if (referenceToScenario.isRemoved) {
        return null
    }
    val service = ScenarioService[referenceToScenario] ?: return null

    val resolvedReference = referenceToScenario.resolve()
    if (resolvedReference is KtParameter) {
        val parameterName = resolvedReference.name ?: return null
        // TODO Maybe make a better search for value of parameter
        return (parentState?.callExpression?.argumentExpressionOrDefaultValue(parameterName) as? KtReferenceExpression)
            ?.let { service.resolveScenario(it) }
    }
    return service.resolveScenario(referenceToScenario)
}

val State.identifierReference: PsiElement?
    get() = when (this.identifier) {
        is StateIdentifier.ExpressionIdentifier -> identifier.valueArgumentElement
        is StateIdentifier.NoIdentifier -> null
        is StateIdentifier.PredefinedIdentifier -> identifier.container
    }

val State.root: State
    get() = parent?.root ?: this

val State.isTopState: Boolean
    get() = parent === scenario.innerState

val State.isRootState: Boolean
    get() = this === scenario.innerState

fun State.allStates(previousStates: MutableList<State> = mutableListOf()): List<State> {
    if (this in previousStates) {
        logger.warn("Detect recursive append. $this")
        return emptyList()
    }

    previousStates += this

    val statesList = states.toMutableList()
    statesList += appends.flatMap { it.resolve()?.innerState?.allStates(previousStates) ?: emptyList() }
    if (this.isRootState) {
        statesList += scenario.topLevelAppends.filter { it.referenceToScenario.isExist }
            .flatMap { it.resolve()?.innerState?.allStates(previousStates) ?: emptyList() }
    }

    return statesList
}

fun contains(state: State, element: PsiElement) = element.textRange.startOffset in state.callExpression.textRange

private val logger = Logger.getInstance("Scenario.kt")