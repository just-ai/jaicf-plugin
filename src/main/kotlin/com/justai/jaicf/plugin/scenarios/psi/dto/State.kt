package com.justai.jaicf.plugin.scenarios.psi.dto

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.scenarios.psi.builders.buildState
import com.justai.jaicf.plugin.scenarios.psi.dto.StateIdentifier.ExpressionIdentifier
import com.justai.jaicf.plugin.scenarios.psi.dto.StateIdentifier.NoIdentifier
import com.justai.jaicf.plugin.scenarios.psi.dto.StateIdentifier.PredefinedIdentifier
import com.justai.jaicf.plugin.scenarios.transition.withoutLeadSlashes
import com.justai.jaicf.plugin.utils.stringValueOrNull
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression

/**
 * Внутреннее представление стейта.
 *
 * @property project в котором находится стейт
 * @property identifier это указанное имя стейта, предопределённое название или отсутствие идентификатора
 * @property parent стейт в котором находится текущий стейт
 * @property scenario в котором находится стейт
 * @property stateExpression выражение в котором содержится тело стейта
 * @property appends содержащиеся непосредственно внутри стейта
 * @property states содержащиеся непосредственно внутри стейта
 *
 * @see buildState
 */
class State(
    val project: Project,
    val identifier: StateIdentifier,
    val parent: State?,
    val scenario: Scenario,
    val stateExpression: KtCallExpression,
) {
    lateinit var appends: List<Append>
    lateinit var states: List<State>
}

/**
 * Идентификатор стейта позволяющий определить its имя.
 *
 * [PredefinedIdentifier] - идентификатор для стейтов у которых в аннотации `@StateDeclaration` указан литерал
 * [ExpressionIdentifier] - идентификатор для стейтов содержащих аннотацию `@StateName`
 * [NoIdentifier] - идентификатор для стейтов у которых не указано имя
 */
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
        val errorCauseMessage: String = "",
    ) : StateIdentifier() {
        override fun resolveText(): String? = null
    }
}

val State.name get() = identifier.resolveText()

val State.nameWithoutLeadSlashes get() = identifier.resolveText()?.withoutLeadSlashes()

val State.identifierReference: PsiElement?
    get() = when (this.identifier) {
        is ExpressionIdentifier -> identifier.valueArgumentElement
        is NoIdentifier -> null
        is PredefinedIdentifier -> identifier.container
    }

/**
 * Корневой стейт сценария в котором находится текущий стейт.
 */
val State.root: State
    get() = parent?.root ?: this

/**
 * Стейт который находится сразу за корневым. У таких стейтов путь состоит только из идентификатора стейта.
 */
val State.isTopState: Boolean
    get() = parent === scenario.innerState

/**
 * A value indicating that the stack is root. For example, the internal scenario state.
 */
val State.isRootState: Boolean
    get() = this === scenario.innerState

/**
 * Все стейты которые непосредственно содержит стейт. НЕ используются appends.
 */
val State.nestedStates: List<State>
    get() = states + states.flatMap(State::nestedStates)
