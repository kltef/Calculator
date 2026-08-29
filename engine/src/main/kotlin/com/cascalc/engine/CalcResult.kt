package com.cascalc.engine

/** Outcome of evaluating one expression. */
sealed interface CalcResult {

    /** Nothing to evaluate (blank or whitespace-only input). */
    data object Empty : CalcResult

    /**
     * @param exact       the exact, display-formatted result (`1/2`, `2*Sqrt(2)`)
     * @param approximate a decimal approximation, or null when it adds nothing
     *                    (the exact result is already a plain integer/decimal)
     * @param raw         Symja's own `toString` form, useful for feeding the
     *                    result back into a later calculation
     * @param steps       a worked derivation, when one could be generated
     * @param note        a short remark about the result (which variable was
     *                    solved for, what an assignment stored, and so on)
     */
    data class Success(
        val exact: String,
        val approximate: String?,
        val raw: String,
        val steps: List<SolutionStep> = emptyList(),
        val note: String? = null,
    ) : CalcResult

    data class Failure(val kind: ErrorKind, val message: String) : CalcResult

    enum class ErrorKind { SYNTAX, UNDEFINED, TIMEOUT, INTERNAL }
}
