package com.justai.jaicf.plugin.scenarios.transition

import com.intellij.openapi.util.TextRange
import com.justai.jaicf.plugin.scenarios.psi.dto.State
import com.justai.jaicf.plugin.scenarios.psi.dto.nameWithoutLeadSlashes
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Slash
import com.justai.jaicf.plugin.scenarios.transition.Lexeme.Transition.Root

data class StatePath(
    val lexemes: List<Lexeme> = emptyList(),
) {

    val transitions: List<Lexeme.Transition>
        get() = lexemes.mapNotNull { it as? Lexeme.Transition }

    override fun toString() =
        if (lexemes.singleOrNull() == Root) Slash.identifier
        else lexemes.joinToString(separator = "") { it.identifier }

    companion object {
        fun parse(path: String) = StatePath(recursiveParse(path, true))

        private fun recursiveParse(path: String, isBegin: Boolean = false): List<Lexeme> =
            if (path.isEmpty())
                emptyList()
            else
                with(Lexeme.getFirstLexeme(path, isBegin)) {
                    listOf(this) + recursiveParse(path.substringAfter(identifier))
                }
    }
}

sealed class Lexeme(open val identifier: String) {

    sealed class Transition(identifier: String) : Lexeme(identifier) {

        object Root : Transition("")

        object Revert : Transition("..")

        object Current : Transition(".")

        data class StateId(override val identifier: String) : Transition(identifier) {
            fun transitToOneOf(states: List<State>) =
                states.firstOrNull { canTransitTo(it) }

            fun canTransitTo(state: State) = identifier == state.nameWithoutLeadSlashes
        }
    }

    object Slash : Lexeme("/")

    object Empty : Lexeme("")

    companion object {
        fun getFirstLexeme(path: String, isBegin: Boolean) = when {
            path.isEmpty() -> Empty
            isBegin && path.startsWith(Slash.identifier) -> Root
            path.startsWith(Slash.identifier) -> Slash
            path.startsWith(Transition.Revert.identifier + Slash.identifier) ||
                path == Transition.Revert.identifier -> Transition.Revert
            path.startsWith(Transition.Current.identifier + Slash.identifier) ||
                path == Transition.Current.identifier -> Transition.Current
            else -> Transition.StateId(path.substringBefore(Slash.identifier))
        }
    }
}

val StatePath.parent
    get() = if (lexemes.isEmpty())
        this
    else
        StatePath(lexemes.subList(0, lexemes.size - 1))

operator fun StatePath.plus(transition: Lexeme.Transition): StatePath {
    return when {
        lexemes.isEmpty() ->
            StatePath(listOf(transition))

        lexemes.last() == Slash ->
            StatePath(lexemes + transition)

        else ->
            StatePath(lexemes + Slash + transition)
    }
}

@Suppress("UNCHECKED_CAST")
fun StatePath.transitionsWithRanges(): List<Pair<Lexeme.Transition, TextRange>> {
    return lexemesWithRanges()
        .filter { it.first is Lexeme.Transition }
        .map { it as Pair<Lexeme.Transition, TextRange> }
}

private fun StatePath.lexemesWithRanges(): List<Pair<Lexeme, TextRange>> {
    var lastRange = TextRange.EMPTY_RANGE
    return lexemes.map { lexeme ->
        lastRange = TextRange(lastRange.endOffset, lastRange.endOffset + lexeme.identifier.length)
        Pair(lexeme, lastRange)
    }
}

internal fun String.withoutLeadSlashes() =
    if (contains('/'))
        this.dropWhile { it == '/' }
    else
        this
