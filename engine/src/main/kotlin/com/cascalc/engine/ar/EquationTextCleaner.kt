package com.cascalc.engine.ar

/**
 * Turns a line of OCR text into something the calculator can evaluate.
 *
 * Recognisers confuse characters that look alike, and handwriting makes it
 * worse. The substitutions here are only applied where the surrounding
 * characters make the reading unambiguous — replacing every `l` with `1`
 * unconditionally would break `log` and `ln`.
 *
 * Lines that do not look like maths are rejected, because an AR overlay that
 * confidently answers a shopping list is worse than one that stays quiet.
 */
object EquationTextCleaner {

    private val MATH_CHARACTERS = "0123456789+-*/^=().,xyztπ√ ".toSet()

    fun clean(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        text = text
            .replace('×', '*')
            .replace('⋅', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace('“', '"')
            .replace('”', '"')
            .replace("²", "^2")
            .replace("³", "^3")

        text = fixDigitLookalikes(text)
        text = text.trimEnd('=', ' ')

        if (!looksLikeMaths(text)) return null
        return text.ifBlank { null }
    }

    /**
     * `O`/`o` -> 0 and `l`/`I` -> 1, but only when adjacent to a digit or an
     * operator, so `log`, `ln`, `cos` and variable names survive.
     */
    private fun fixDigitLookalikes(text: String): String {
        val characters = text.toCharArray()
        for (index in characters.indices) {
            val c = characters[index]
            val replacement = when (c) {
                'O', 'o' -> '0'
                'l', 'I', '|' -> '1'
                'S' -> '5'
                else -> continue
            }
            val before = characters.getOrNull(index - 1)
            val after = characters.getOrNull(index + 1)
            if (isNumericContext(before) || isNumericContext(after)) {
                characters[index] = replacement
            }
        }
        return String(characters)
    }

    private fun isNumericContext(c: Char?): Boolean =
        c != null && (c.isDigit() || c in "+-*/^=().")

    /**
     * Accepts a line only if it is arithmetic-shaped: made of maths characters,
     * containing at least one digit and at least one operator or equals sign.
     */
    private fun looksLikeMaths(text: String): Boolean {
        if (text.length < MIN_LENGTH) return false
        if (text.none { it.isDigit() }) return false
        if (text.none { it in "+-*/^=" }) return false

        val recognised = text.count { it in MATH_CHARACTERS || it.isLetter() }
        if (recognised < text.length) return false

        // Mostly letters means it is prose that happens to contain a number.
        val letters = text.count { it.isLetter() }
        return letters <= text.length * MAX_LETTER_FRACTION
    }

    private const val MIN_LENGTH = 3
    private const val MAX_LETTER_FRACTION = 0.4
}
