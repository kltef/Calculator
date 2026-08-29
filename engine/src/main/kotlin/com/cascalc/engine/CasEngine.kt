package com.cascalc.engine

import org.matheclipse.core.eval.ExprEvaluator
import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IBuiltInSymbol
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
    private val stepSolver = StepSolver(::evalRaw)

    val variables = VariableStore()

    fun evaluate(
        input: String,
        angleMode: AngleMode = AngleMode.RADIANS,
        action: Action = Action.EVALUATE,
    ): CalcResult {
        if (input.isBlank()) return CalcResult.Empty

        return try {
            when (val command = CommandParser.parse(input)) {
                is Command.Assignment ->
                    if (action == Action.EVALUATE) {
                        assign(command, angleMode)
                    } else {
                        // "Simplify a = 5" is meaningless; treat it as an equation.
                        transform(Command.Equation(command.name, command.valueText), action, angleMode)
                    }

                is Command.Equation -> transform(command, action, angleMode)
                is Command.Expression -> transform(command, action, angleMode)
            }
        } catch (e: SyntaxError) {
            CalcResult.Failure(CalcResult.ErrorKind.SYNTAX, syntaxMessage(e))
        } catch (e: StackOverflowError) {
            CalcResult.Failure(CalcResult.ErrorKind.INTERNAL, "Expression is too deeply nested")
        } catch (e: Exception) {
            CalcResult.Failure(CalcResult.ErrorKind.INTERNAL, friendlyMessage(e))
        }
    }

    // --- variables ---------------------------------------------------------

    /** Removes one binding. */
    fun clearVariable(name: String) = variables.remove(name)

    /** Forgets everything: variables and any evaluator state. */
    fun reset() {
        variables.clear()
        evaluator.evalEngine.init()
        evaluator.clearVariables()
    }

    private fun assign(command: Command.Assignment, angleMode: AngleMode): CalcResult {
        val name = command.name
        reservedNameProblem(name)?.let {
            return CalcResult.Failure(CalcResult.ErrorKind.SYNTAX, it)
        }
        if (command.valueText.isBlank()) {
            return CalcResult.Failure(CalcResult.ErrorKind.SYNTAX, "Give $name a value, e.g. $name = 5")
        }

        val definition = InputNormalizer.normalize(command.valueText, angleMode)

        // Resolve against the *other* variables so a definition is stored in
        // terms of what it meant when written, and check it does not lead back
        // to itself: ReplaceRepeated on a cycle silently runs to the recursion
        // limit and returns garbage rather than failing.
        val resolved = evalRaw(substituted(definition, excluding = setOf(name)))
        if (!resolved.isFree(F.symbol(name))) {
            return CalcResult.Failure(
                CalcResult.ErrorKind.SYNTAX,
                "$name can't be defined in terms of itself",
            )
        }

        variables.define(name, resolved.toString())

        return CalcResult.Success(
            exact = "$name = ${ResultFormatter.formatExact(resolved)}",
            approximate = approximate(resolved),
            raw = resolved.toString(),
            note = "Stored. Use $name in later calculations.",
        )
    }

    /** Names Symja already owns, which would silently shadow a builtin. */
    private fun reservedNameProblem(name: String): String? {
        val normalized = InputNormalizer.normalize(name, AngleMode.RADIANS)
        val symbol = try {
            evalRaw(normalized)
        } catch (e: SyntaxError) {
            return "$name isn't a usable variable name"
        }
        return when {
            symbol is IBuiltInSymbol || normalized != name ->
                "$name is a built-in name — pick another"
            else -> null
        }
    }

    // --- evaluation --------------------------------------------------------

    private fun transform(command: Command, action: Action, angleMode: AngleMode): CalcResult {
        return when (action) {
            Action.SOLVE -> solve(command, angleMode)
            else -> {
                val expression = when (command) {
                    is Command.Expression -> InputNormalizer.normalize(command.text, angleMode)
                    is Command.Equation -> {
                        val left = InputNormalizer.normalize(command.leftText, angleMode)
                        val right = InputNormalizer.normalize(command.rightText, angleMode)
                        "($left) == ($right)"
                    }
                    is Command.Assignment -> return CalcResult.Failure(
                        CalcResult.ErrorKind.INTERNAL,
                        "Unexpected assignment",
                    )
                }
                if (expression.isBlank()) return CalcResult.Empty
                finish(evalRaw(wrap(substituted(expression), action)))
            }
        }
    }

    private fun wrap(expression: String, action: Action): String = when (action) {
        Action.EVALUATE -> expression
        Action.SIMPLIFY -> "Simplify($expression)"
        Action.EXPAND -> "Expand($expression)"
        Action.FACTOR -> "Factor($expression)"
        Action.SOLVE -> expression // handled separately
    }

    private fun finish(result: IExpr, steps: List<SolutionStep> = emptyList(), note: String? = null): CalcResult {
        undefinedReason(result)?.let {
            return CalcResult.Failure(CalcResult.ErrorKind.UNDEFINED, it)
        }
        return CalcResult.Success(
            exact = ResultFormatter.formatExact(result),
            approximate = approximate(result),
            raw = result.toString(),
            steps = steps,
            note = note,
        )
    }

    // --- solving -----------------------------------------------------------

    private fun solve(command: Command, angleMode: AngleMode): CalcResult {
        val (leftText, rightText) = when (command) {
            is Command.Equation -> command.leftText to command.rightText
            is Command.Expression -> command.text to "0" // "x^2 - 4" means "= 0"
            is Command.Assignment -> command.name to command.valueText
        }

        val left = InputNormalizer.normalize(leftText, angleMode)
        val right = InputNormalizer.normalize(rightText, angleMode)
        if (left.isBlank()) return CalcResult.Empty

        // The unknown must stay symbolic, so it is excluded from substitution.
        val unknown = chooseUnknown(left, right)
            ?: return CalcResult.Failure(
                CalcResult.ErrorKind.SYNTAX,
                "There's no unknown to solve for — add a variable like x",
            )

        val exclude = setOf(unknown)
        val equation = "(${substituted(left, exclude)}) == (${substituted(right, exclude)})"
        val solutions = evalRaw("Solve($equation, $unknown)")

        val standardForm = evalRaw(
            "Expand((${substituted(left, exclude)}) - (${substituted(right, exclude)}))",
        ).toString()
        val steps = stepSolver.steps(standardForm, unknown) ?: emptyList()

        return when {
            !solutions.isList -> finish(solutions, steps, "Solved for $unknown")
            solutions.size() == 1 && solutions.isEmpty ->
                CalcResult.Failure(CalcResult.ErrorKind.UNDEFINED, "No solution")
            // Solve returns {{}} -- one solution set, with no constraint on the
            // unknown -- when the equation holds for every value of it.
            isIdentity(solutions) -> CalcResult.Success(
                exact = "Any value of $unknown",
                approximate = null,
                raw = solutions.toString(),
                steps = steps,
                note = "The equation is true for every $unknown",
            )

            else -> {
                val formatted = formatSolutions(solutions, unknown)
                    ?: return CalcResult.Failure(CalcResult.ErrorKind.UNDEFINED, "No solution")
                CalcResult.Success(
                    exact = formatted,
                    approximate = null,
                    raw = solutions.toString(),
                    steps = steps,
                    note = "Solved for $unknown",
                )
            }
        }
    }

    private fun isIdentity(solutions: IExpr): Boolean =
        solutions.size() == 2 &&
            solutions.getAt(1).isList &&
            solutions.getAt(1).size() == 1

    /**
     * Picks the variable to solve for: the single free symbol, preferring `x`
     * when several are present (which is what people mean nearly every time).
     */
    private fun chooseUnknown(left: String, right: String): String? {
        val symbols = freeSymbols(evalRaw("Hold(($left) - ($right))")) - variables.names()
        return when {
            symbols.isEmpty() -> null
            "x" in symbols -> "x"
            else -> symbols.minOrNull()
        }
    }

    /** User-defined symbols in an expression tree, ignoring Symja's builtins. */
    private fun freeSymbols(expr: IExpr): Set<String> {
        val found = sortedSetOf<String>()
        fun walk(node: IExpr) {
            if (node.isSymbol && node !is IBuiltInSymbol) {
                found += node.toString()
                return
            }
            if (node.isAST) {
                val ast = node.toString()
                if (ast.isNotEmpty()) {
                    for (i in 0 until node.size()) walk(node.getAt(i))
                }
            }
        }
        walk(expr)
        return found
    }

    /** `{{x->-2},{x->2}}` -> `x = -2,  x = 2`. */
    private fun formatSolutions(solutions: IExpr, unknown: String): String? {
        if (solutions.size() <= 1) return null
        val rendered = (1 until solutions.size()).mapNotNull { i ->
            val rules = solutions.getAt(i)
            if (!rules.isList || rules.size() <= 1) return@mapNotNull null
            (1 until rules.size()).joinToString(",  ") { j ->
                val rule = rules.getAt(j)
                if (rule.size() == 3) {
                    "${rule.getAt(1)} = ${ResultFormatter.formatExact(rule.getAt(2))}"
                } else {
                    ResultFormatter.formatExact(rule)
                }
            }
        }
        return rendered.takeIf { it.isNotEmpty() }?.joinToString(",  ")
    }

    // --- plumbing ----------------------------------------------------------

    /** Wraps [expression] so the user's variable bindings are applied to it. */
    private fun substituted(expression: String, excluding: Set<String> = emptySet()): String =
        if (variables.isEmpty(excluding)) {
            expression
        } else {
            "ReplaceRepeated($expression, ${variables.rulesText(excluding)})"
        }

    private fun evalRaw(symjaExpression: String): IExpr {
        val engine = evaluator.evalEngine
        engine.setTimeConstrainedMillis(System.currentTimeMillis() + timeoutMillis)
        engine.seconds = timeoutMillis / 1000
        return evaluator.eval(symjaExpression)
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

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 5_000
        private const val RECURSION_LIMIT: Short = 256

        init {
            // Symja's symbol tables are expensive to build; warm them once.
            F.initSymbols()
        }
    }
}
