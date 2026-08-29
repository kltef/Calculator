package com.cascalc.engine

/** What a line of input turned out to be. */
sealed interface Command {

    /** `a = 5` — bind a name. */
    data class Assignment(val name: String, val valueText: String) : Command

    /** `x^2 - 4 = 0` — two sides related by `=`. */
    data class Equation(val leftText: String, val rightText: String) : Command

    /** Anything else. */
    data class Expression(val text: String) : Command
}

/**
 * Splits input on a top-level `=` before the text reaches Symja.
 *
 * The distinction between an assignment and an equation is positional, which is
 * how calculators and CAS front-ends conventionally do it: a bare identifier on
 * the left means "store this", anything else means "these two sides are equal".
 * `==` always means equality, never assignment.
 */
object CommandParser {

    private val IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    fun parse(input: String): Command {
        val split = splitOnTopLevelEquals(input)
            ?: return Command.Expression(input.trim())

        val (left, right) = split
        val name = left.trim()
        return if (IDENTIFIER.matches(name)) {
            Command.Assignment(name, right.trim())
        } else {
            Command.Equation(name, right.trim())
        }
    }

    /**
     * Finds a single `=` that is outside any parentheses and is not part of
     * `==`, `<=`, `>=` or `!=`. Returns null when there isn't exactly one.
     */
    private fun splitOnTopLevelEquals(input: String): Pair<String, String>? {
        var depth = 0
        var found = -1
        var i = 0
        while (i < input.length) {
            when (val c = input[i]) {
                '(', '[' -> depth++
                ')', ']' -> depth--
                '=' -> {
                    val previous = input.getOrNull(i - 1)
                    val next = input.getOrNull(i + 1)
                    val partOfComparison =
                        previous == '=' || previous == '<' || previous == '>' || previous == '!' ||
                            next == '='
                    if (!partOfComparison && depth == 0) {
                        if (found != -1) return null // more than one: ambiguous
                        found = i
                    }
                }
                else -> Unit
            }
            i++
        }
        if (found == -1) return null
        return input.substring(0, found) to input.substring(found + 1)
    }
}
