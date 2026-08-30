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
        return (factors as? CalcResult.Success)?.let { "Prime factors: ${it.exact}" }
    }
}
