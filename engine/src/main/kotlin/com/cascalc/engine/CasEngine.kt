package com.cascalc.engine

import org.matheclipse.core.eval.ExprEvaluator
import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IExpr
import org.matheclipse.parser.client.SyntaxError

/**
 * The calculator's math core, wrapping Symja.
 *
 * Everything is evaluated exactly first: `1/3 + 1/6` stays `1/2` instead of
 * collapsing to `0.5`. A decimal approximation is computed separately and only
 * reported when it tells the user something the exact form doesn't.
 *
 * Symja's evaluator holds mutable state and is **not** thread-safe, so this
 * class is not either. Confine one instance to one thread (see [CalculatorSession]).
 */
class CasEngine(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {

    private val evaluator = ExprEvaluator(false, RECURSION_LIMIT)

    fun evaluate(input: String, angleMode: AngleMode = AngleMode.RADIANS): CalcResult {
        if (input.isBlank()) return CalcResult.Empty

        val normalized = try {
            InputNormalizer.normalize(input, angleMode)
        } catch (e: RuntimeException) {
            return CalcResult.Failure(CalcResult.ErrorKind.SYNTAX, "Couldn't read that expression")
        }
        if (normalized.isBlank()) return CalcResult.Empty

        val exact = try {
            evaluateWithTimeout(normalized)
        } catch (e: SyntaxError) {
            return CalcResult.Failure(CalcResult.ErrorKind.SYNTAX, syntaxMessage(e))
        } catch (e: TimeoutException) {
            return CalcResult.Failure(
                CalcResult.ErrorKind.TIMEOUT,
                "That took too long to work out",
            )
        } catch (e: StackOverflowError) {
            return CalcResult.Failure(CalcResult.ErrorKind.INTERNAL, "Expression is too deeply nested")
        } catch (e: Exception) {
            return CalcResult.Failure(CalcResult.ErrorKind.INTERNAL, friendlyMessage(e))
        }

        undefinedReason(exact)?.let {
            return CalcResult.Failure(CalcResult.ErrorKind.UNDEFINED, it)
        }

        return CalcResult.Success(
            exact = ResultFormatter.formatExact(exact),
            approximate = approximate(exact),
            raw = exact.toString(),
        )
    }

    /** Forgets all evaluator state (variables, assignments) — used by "clear all". */
    fun reset() {
        evaluator.evalEngine.init()
        evaluator.clearVariables()
    }

    private fun evaluateWithTimeout(normalized: String): IExpr {
        val engine = evaluator.evalEngine
        engine.setTimeConstrainedMillis(System.currentTimeMillis() + timeoutMillis)
        engine.seconds = timeoutMillis / 1000
        return evaluator.eval(normalized)
    }

    /**
     * A decimal form of [expr], or null when it would just repeat the exact
     * result (integers, and results that are already decimal).
     */
    private fun approximate(expr: IExpr): String? {
        if (expr.isInteger) return null
        if (expr.isReal && !expr.isRational) return null // already a decimal
        if (!expr.isNumericFunction(true)) return null   // symbolic: no meaningful decimal

        return try {
            val numeric = evaluator.evalEngine.evalN(expr)
            val d = numeric.evalf()
            if (d.isNaN() || d.isInfinite()) null else ResultFormatter.formatDouble(d)
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun undefinedReason(expr: IExpr): String? {
        val text = expr.toString()
        return when {
            expr.isIndeterminate || text == "Indeterminate" -> "Undefined"
            text == "ComplexInfinity" -> "Undefined — division by zero"
            else -> null
        }
    }

    private fun syntaxMessage(e: SyntaxError): String {
        val detail = e.message?.substringAfter(" - ")?.trim().orEmpty()
        return if (detail.isEmpty()) "Syntax error" else "Syntax error: $detail"
    }

    private fun friendlyMessage(e: Exception): String =
        e.message?.takeIf { it.isNotBlank() } ?: "Couldn't evaluate that"

    class TimeoutException : RuntimeException("Evaluation timed out")

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 5_000
        private const val RECURSION_LIMIT: Short = 256

        init {
            // Symja's symbol tables are expensive to build; warm them once.
            F.initSymbols()
        }
    }
}
