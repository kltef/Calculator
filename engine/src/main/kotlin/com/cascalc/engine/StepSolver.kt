package com.cascalc.engine

import org.matheclipse.core.interfaces.IExpr

/**
 * Produces a worked solution for equations that can be solved by a procedure a
 * person would recognise — linear and quadratic in one unknown.
 *
 * Symja can *solve* far more than this, but it does not explain itself: `Solve`
 * returns roots with no derivation. So the steps are generated here by driving
 * the standard school method and asking Symja to evaluate each intermediate
 * quantity exactly.
 *
 * Anything outside that range returns null, and the caller falls back to
 * reporting the answer without a derivation. Claiming to show work and then
 * inventing it would be worse than showing none.
 *
 * @param eval evaluates a Symja expression; supplied by [CasEngine] so this
 *             class holds no evaluator state of its own
 */
class StepSolver(private val eval: (String) -> IExpr) {

    /**
     * @param standardForm the equation rearranged to `<expr> = 0`, in Symja syntax
     * @param unknown the variable being solved for
     */
    fun steps(standardForm: String, unknown: String): List<SolutionStep>? {
        if (!isTrue("PolynomialQ($standardForm, $unknown)")) return null

        val degree = eval("Exponent($standardForm, $unknown)")
        return when {
            degree.isOne -> linearSteps(standardForm, unknown)
            degree.toString() == "2" -> quadraticSteps(standardForm, unknown)
            else -> null
        }
    }

    /** `c1*x + c0 = 0`  ->  `x = -c0/c1` */
    private fun linearSteps(standardForm: String, unknown: String): List<SolutionStep>? {
        val coefficients = coefficientList(standardForm, unknown) ?: return null
        if (coefficients.size != 2) return null
        val (c0, c1) = coefficients
        if (isZero(c1)) return null

        val steps = mutableListOf<SolutionStep>()
        steps += SolutionStep(
            "Move every term to one side, leaving 0 on the other.",
            "${display(standardForm)} = 0",
        )
        steps += SolutionStep(
            "Move the constant across.",
            "${display("$c1 * $unknown")} = ${display("-($c0)")}",
        )
        steps += SolutionStep(
            "Divide both sides by ${display(c1)}.",
            "$unknown = ${display("-($c0)/($c1)")}",
        )
        return steps
    }

    /** `a*x^2 + b*x + c = 0` via the quadratic formula. */
    private fun quadraticSteps(standardForm: String, unknown: String): List<SolutionStep>? {
        val coefficients = coefficientList(standardForm, unknown) ?: return null
        if (coefficients.size != 3) return null
        val (c, b, a) = coefficients
        if (isZero(a)) return null

        val discriminant = eval("($b)^2 - 4*($a)*($c)").toString()

        val steps = mutableListOf<SolutionStep>()
        steps += SolutionStep(
            "Write the equation in standard form a$unknown² + b$unknown + c = 0.",
            "${display(standardForm)} = 0",
        )
        steps += SolutionStep(
            "Read off the coefficients.",
            "a = ${display(a)},  b = ${display(b)},  c = ${display(c)}",
        )
        steps += SolutionStep(
            "Work out the discriminant Δ = b² − 4ac.",
            "Δ = ${display(discriminant)}",
        )
        steps += SolutionStep(discriminantMeaning(discriminant, unknown))
        steps += SolutionStep(
            "Apply the quadratic formula.",
            "$unknown = (−b ± √Δ) / 2a = (${display("-($b)")} ± √${display(discriminant)}) / ${display("2*($a)")}",
        )

        val roots = rootsOf(a, b, c, discriminant)
        steps += SolutionStep(
            if (roots.size == 1) "Simplify." else "Simplify each branch.",
            roots.joinToString("    ") { "$unknown = $it" },
        )
        return steps
    }

    private fun discriminantMeaning(discriminant: String, unknown: String): String {
        val sign = eval("Sign($discriminant)").toString()
        return when (sign) {
            "1" -> "Δ is positive, so there are two distinct real solutions."
            "0" -> "Δ is zero, so the two solutions coincide — one repeated root."
            "-1" -> "Δ is negative, so there are no real solutions; the two roots are complex."
            else -> "The sign of Δ depends on the remaining symbols, so both branches are kept."
        }
    }

    private fun rootsOf(a: String, b: String, c: String, discriminant: String): List<String> {
        val minus = display("(-($b) - Sqrt($discriminant)) / (2*($a))")
        val plus = display("(-($b) + Sqrt($discriminant)) / (2*($a))")
        return if (isZero(discriminant)) listOf(plus) else listOf(minus, plus)
    }

    /** `CoefficientList` ascending: index 0 is the constant term. */
    private fun coefficientList(expression: String, unknown: String): List<String>? {
        val list = eval("CoefficientList($expression, $unknown)")
        if (!list.isList) return null
        return (1..list.size() - 1).map { list.getAt(it).toString() }
    }

    private fun display(symjaExpression: String): String =
        ResultFormatter.prettify(eval(symjaExpression).toString())

    private fun isZero(symjaExpression: String): Boolean =
        eval("PossibleZeroQ($symjaExpression)").isTrue

    private fun isTrue(symjaExpression: String): Boolean = eval(symjaExpression).isTrue
}
