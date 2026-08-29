package com.cascalc.engine

import org.matheclipse.core.interfaces.IExpr

/**
 * Turns a Symja expression into something a calculator user recognises.
 *
 * The rewrites here are deliberately conservative: only unambiguous textual
 * substitutions are applied, because a formatter that silently changes the
 * meaning of a result is worse than one that shows Symja's own syntax.
 */
object ResultFormatter {

    private const val SIGNIFICANT_DIGITS = 12

    fun formatExact(expr: IExpr): String = stackMatrixRows(prettify(expr.toString()))

    /**
     * Puts each row of a matrix on its own line.
     *
     * Symja wraps its own output based on line width, so the same matrix can
     * come back on one line or several depending on how wide the numbers are.
     * That is fine for a console and useless for a UI, so wrapping is disabled
     * (see [SymjaConfiguration]) and rows are split here instead — always, and
     * only at the top level.
     */
    fun stackMatrixRows(text: String): String {
        if (!text.startsWith("{{") || !text.endsWith("}}")) return text

        val inner = text.substring(1, text.length - 1)
        val rows = splitTopLevel(inner)
        // Every element must itself be a row for this to be a matrix.
        if (rows.size < 2 || rows.any { !it.startsWith("{") || !it.endsWith("}") }) return text
        return rows.joinToString(",\n", prefix = "{", postfix = "}")
    }

    /** Splits on commas that are not nested inside braces or parentheses. */
    private fun splitTopLevel(text: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        text.forEachIndexed { index, c ->
            when (c) {
                '{', '(', '[' -> depth++
                '}', ')', ']' -> depth--
                ',' -> if (depth == 0) {
                    parts += text.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        parts += text.substring(start).trim()
        return parts
    }

    /** Applies only substitutions that cannot change how an expression parses. */
    fun prettify(symjaText: String): String =
        symjaText
            .replace("*", "·")     // 2*Sqrt(2) -> 2·Sqrt(2)
            .replace("Sqrt(", "√(") // -> √(
            .replace("Pi", "π")     // -> π
            .replace("Infinity", "∞")
            // Symja indents wrapped continuation lines; drop the stray indent.
            .replace("\n ", "\n")

    /**
     * Formats a numeric approximation, trimming the noise digits that come with
     * binary floating point (`0.30000000000000004` -> `0.3`).
     */
    fun formatDouble(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        if (value == 0.0) return "0"

        val magnitude = kotlin.math.abs(value)
        if (magnitude >= 1e12 || magnitude < 1e-6) {
            return String.format("%.${SIGNIFICANT_DIGITS - 1}e", value)
                .replace(Regex("0+e"), "e")
                .replace(Regex("\\.e"), "e")
        }

        val rounded = java.math.BigDecimal(value)
            .round(java.math.MathContext(SIGNIFICANT_DIGITS))
            .stripTrailingZeros()
        return rounded.toPlainString()
    }
}
