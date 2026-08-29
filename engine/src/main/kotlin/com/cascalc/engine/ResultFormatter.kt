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

    fun formatExact(expr: IExpr): String = prettify(expr.toString())

    /** Applies only substitutions that cannot change how an expression parses. */
    fun prettify(symjaText: String): String =
        symjaText
            .replace("*", "·")     // 2*Sqrt(2) -> 2·Sqrt(2)
            .replace("Sqrt(", "√(") // -> √(
            .replace("Pi", "π")     // -> π
            .replace("Infinity", "∞")

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
