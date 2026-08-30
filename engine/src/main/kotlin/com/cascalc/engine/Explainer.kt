package com.cascalc.engine

/**
 * Describes what an expression *is*, not just what it equals.
 *
 * Pointing at something and asking about it deserves more than a number. This
 * says what kind of object it is and the facts a person would want next — the
 * degree, how many real roots, whether it factors — each one computed rather
 * than asserted, so it cannot describe an equation it has not actually examined.
 */
class Explainer(private val engine: CasEngine) {

    /** @param headline what it is; @param facts supporting details */
    data class Explanation(val headline: String, val facts: List<String>)

    fun explain(input: String, angleMode: AngleMode = AngleMode.RADIANS): Explanation? {
        if (input.isBlank()) return null

        val isEquation = CommandParser.parse(input) is Command.Equation
        return if (isEquation) explainEquation(input, angleMode) else explainExpression(input, angleMode)
    }

    private fun explainEquation(input: String, angleMode: AngleMode): Explanation {
        val facts = mutableListOf<String>()
        val degree = degreeOf(input, angleMode)

        val headline = when (degree) {
            1 -> "Linear equation"
            2 -> "Quadratic equation"
            3 -> "Cubic equation"
            null -> "Equation"
            else -> "Degree-$degree equation"
        }

        when (val solved = engine.evaluate(input, angleMode, Action.SOLVE)) {
            is CalcResult.Success -> {
                val count = solved.exact.split(",").size
                facts += when {
                    solved.exact.startsWith("Any value") -> "True for every value — an identity"
                    count == 1 -> "One solution: ${solved.exact}"
                    else -> "$count solutions: ${solved.exact}"
                }
                if (solved.steps.isNotEmpty()) {
                    facts += "${solved.steps.size} steps to solve it"
                }
            }
            is CalcResult.Failure -> facts += solved.message
            CalcResult.Empty -> Unit
        }

        if (degree == 2) {
            discriminantFact(input, angleMode)?.let { facts += it }
        }
        return Explanation(headline, facts)
    }

    private fun explainExpression(input: String, angleMode: AngleMode): Explanation {
        val facts = mutableListOf<String>()
        val result = engine.evaluate(input, angleMode)

        val headline = when {
            result !is CalcResult.Success -> "Expression"
            result.raw.toIntOrNull() != null -> "Whole number"
            result.exact.contains("/") -> "Fraction"
            result.approximate != null -> "Exact value"
            else -> "Expression"
        }

        if (result is CalcResult.Success) {
            facts += "Equals ${result.exact}"
            result.approximate?.let { facts += "About $it" }

            val whole = result.raw.toLongOrNull()
            if (whole != null && whole > 1) {
                factorFact(whole)?.let { facts += it }
            }
            val expanded = engine.evaluate(input, angleMode, Action.FACTOR)
            if (expanded is CalcResult.Success && expanded.exact != result.exact) {
                facts += "Factors as ${expanded.exact}"
            }
        }
        return Explanation(headline, facts)
    }

    private fun degreeOf(input: String, angleMode: AngleMode): Int? {
        val command = CommandParser.parse(input) as? Command.Equation ?: return null
        val moved = "(${command.leftText}) - (${command.rightText})"
        val variable = engine.plotVariable(moved, angleMode) ?: return null
        val result = engine.evaluate("Exponent(Expand($moved), $variable)", angleMode)
        return (result as? CalcResult.Success)?.raw?.toIntOrNull()
    }

    private fun discriminantFact(input: String, angleMode: AngleMode): String? {
        val solved = engine.evaluate(input, angleMode, Action.SOLVE)
        val steps = (solved as? CalcResult.Success)?.steps ?: return null
        return steps.firstOrNull { it.explanation.contains("Δ") }?.explanation
    }

    private fun factorFact(value: Long): String? {
        val result = engine.evaluate("PrimeQ($value)")
        if (result is CalcResult.Success && result.raw == "True") {
            return "$value is prime"
        }
        val factors = engine.evaluate("FactorInteger($value)")
        val raw = (factors as? CalcResult.Success)?.raw ?: return null
        return formatFactorisation(raw)?.let { "Prime factors: $it" }
    }

    /**
     * Turns Symja's `{{2,1},{71,1},{809,1}}` into `2 × 71 × 809`.
     *
     * The pair-of-pairs form is how the library represents base and exponent,
     * and it is unreadable on a card floating over a page. Exponents of one are
     * dropped and the rest are shown as superscripts.
     */
    fun formatFactorisation(raw: String): String? {
        if (!raw.startsWith("{{") || !raw.endsWith("}}")) return null
        val pairs = splitTopLevel(raw.substring(1, raw.length - 1))

        val parts = pairs.map { pair ->
            if (!pair.startsWith("{") || !pair.endsWith("}")) return null
            val fields = splitTopLevel(pair.substring(1, pair.length - 1))
            if (fields.size != 2) return null
            val base = fields[0].trim()
            val exponent = fields[1].trim()
            if (exponent == "1") base else "$base${superscript(exponent)}"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" × ")
    }

    private fun superscript(number: String): String =
        number.map { SUPERSCRIPTS[it] ?: it }.joinToString("")

    /** Splits on commas outside any braces. */
    private fun splitTopLevel(text: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        text.forEachIndexed { index, c ->
            when (c) {
                '{' -> depth++
                '}' -> depth--
                ',' -> if (depth == 0) {
                    parts += text.substring(start, index)
                    start = index + 1
                }
            }
        }
        parts += text.substring(start)
        return parts.map { it.trim() }
    }

    private companion object {
        val SUPERSCRIPTS = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        )
    }
}
