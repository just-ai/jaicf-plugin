package com.justai.jaicf.plugin.services.managers.dto

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.stringValueOrNull
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression

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

fun contains(state: State, element: PsiElement) = element.textRange.startOffset in state.stateExpression.textRange

val State.nestedStates: List<State>
    get() = states + states.flatMap(State::nestedStates)
