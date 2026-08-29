package com.cascalc.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Thread-confined front door to the engine.
 *
 * [CasEngine] (and Symja underneath it) is not thread-safe, so every evaluation
 * is funnelled through one dedicated background thread. Callers get suspend
 * functions and never touch the engine directly.
 *
 * The engine is also *constructed* on that thread, not by whoever constructs
 * this class. Building it means initialising Symja's entire builtin catalogue,
 * which takes seconds — long enough to freeze an Android app if it happened on
 * the main thread — and it is what makes the confinement genuine rather than
 * merely claimed.
 */
class CalculatorSession(
    private val engineFactory: () -> CasEngine = { CasEngine() },
    val history: CalculationHistory = CalculationHistory(),
) : AutoCloseable {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "cas-engine").apply { isDaemon = true }
    }
    private val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    private var engine: CasEngine? = null

    /**
     * Why the engine could not be started, if it could not.
     *
     * Symja's initialisation reaches a lot of the platform, and a failure there
     * is a `NoClassDefFoundError` rather than an `Exception`. Letting that
     * propagate would take the process down with no explanation, so it is
     * captured and reported like any other failed calculation.
     */
    private var startupFailure: Throwable? = null

    private val _startupDiagnostic = MutableStateFlow<String?>(null)

    /**
     * The full failure report if the engine could not start, for display.
     *
     * The top-level message is useless on its own — an `ExceptionInInitializerError`
     * carries no message at all — so this walks the whole cause chain and keeps
     * the deepest stack trace, which is the part that names the culprit.
     */
    val startupDiagnostic: StateFlow<String?> = _startupDiagnostic.asStateFlow()

    private val _variables = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The user's variable bindings, for display. */
    val variables: StateFlow<Map<String, String>> = _variables.asStateFlow()

    /** Evaluates without touching history — used for the live preview as you type. */
    suspend fun preview(
        input: String,
        angleMode: AngleMode,
        action: Action = Action.EVALUATE,
    ): CalcResult = withContext(dispatcher) {
        val engine = engineOrNull() ?: return@withContext startupFailureResult()
        engine.evaluate(input, angleMode, action)
    }

    /** Evaluates and, on success, records the calculation in [history]. */
    suspend fun submit(
        input: String,
        angleMode: AngleMode,
        action: Action = Action.EVALUATE,
    ): CalcResult {
        val result = preview(input, angleMode, action)
        if (result is CalcResult.Success) {
            history.record(input, result, angleMode, action)
            publishVariables()
        }
        return result
    }

    suspend fun clearVariable(name: String) {
        withContext(dispatcher) { engineOrNull()?.clearVariable(name) }
        publishVariables()
    }

    suspend fun reset() {
        withContext(dispatcher) { engineOrNull()?.reset() }
        history.clear()
        publishVariables()
    }

    /**
     * Compiles an expression into a numeric function for plotting.
     *
     * Compilation happens on the engine thread. The returned function calls
     * back into Symja, so callers must not sample it from several threads at
     * once — one sampling coroutine at a time.
     */
    suspend fun numericFunction(
        expression: String,
        angleMode: AngleMode,
    ): ((Double) -> Double)? = withContext(dispatcher) {
        val engine = engineOrNull() ?: return@withContext null
        val variable = engine.plotVariable(expression, angleMode) ?: DEFAULT_PLOT_VARIABLE
        engine.numericFunction(expression, variable, angleMode)
    }

    /** The tangent to [expression] at [x], with the variable inferred. */
    suspend fun tangentAt(
        expression: String,
        x: Double,
        angleMode: AngleMode,
    ): TangentLine? = withContext(dispatcher) {
        val engine = engineOrNull() ?: return@withContext null
        val variable = engine.plotVariable(expression, angleMode) ?: DEFAULT_PLOT_VARIABLE
        engine.tangentAt(expression, variable, x, angleMode)
    }

    /** The exact definite integral of [expression] over the given bounds. */
    suspend fun definiteIntegral(
        expression: String,
        from: Double,
        to: Double,
        angleMode: AngleMode,
    ): String? = withContext(dispatcher) {
        val engine = engineOrNull() ?: return@withContext null
        val variable = engine.plotVariable(expression, angleMode) ?: DEFAULT_PLOT_VARIABLE
        engine.definiteIntegral(expression, variable, from, to, angleMode)
    }

    /** A numeric function for f′, so the derivative can be plotted. */
    suspend fun derivativeFunction(
        expression: String,
        angleMode: AngleMode,
    ): ((Double) -> Double)? = withContext(dispatcher) {
        val engine = engineOrNull() ?: return@withContext null
        val variable = engine.plotVariable(expression, angleMode) ?: DEFAULT_PLOT_VARIABLE
        val derivative = engine.evaluate(expression, angleMode, Action.DIFFERENTIATE)
        val body = (derivative as? CalcResult.Success)?.raw ?: return@withContext null
        engine.numericFunction(body, variable, angleMode)
    }

    /**
     * Builds the engine ahead of first use, so the wait happens while the user
     * is still looking at an empty calculator rather than after their first tap.
     */
    suspend fun warmUp() {
        withContext(dispatcher) { engineOrNull() }
    }

    override fun close() {
        executor.shutdownNow()
    }

    /** Call only from [dispatcher]. */
    private fun engineOrNull(): CasEngine? {
        engine?.let { return it }
        if (startupFailure != null) return null
        return try {
            engineFactory().also { engine = it }
        } catch (t: Throwable) {
            startupFailure = t
            _startupDiagnostic.value = describe(t)
            null
        }
    }

    private fun startupFailureResult(): CalcResult {
        val root = startupFailure?.let { rootCauseOf(it) }
        val detail = root?.let { "${it::class.java.simpleName}: ${it.message ?: "no message"}" }.orEmpty()
        return CalcResult.Failure(
            CalcResult.ErrorKind.INTERNAL,
            "The math engine couldn't start. $detail".trim(),
        )
    }

    private fun rootCauseOf(t: Throwable): Throwable {
        var current = t
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current
    }

    /** Cause chain plus the deepest stack trace — enough to identify the culprit. */
    private fun describe(t: Throwable): String = buildString {
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            appendLine("${current::class.java.name}: ${current.message ?: "(no message)"}")
            val next = current.cause
            if (next == null || next === current) break
            append("  caused by ")
            current = next
            depth++
        }
        appendLine()
        rootCauseOf(t).stackTrace.take(MAX_STACK_FRAMES).forEach { appendLine("  at $it") }
    }

    private companion object {
        /** A constant expression still plots — as a horizontal line against x. */
        const val DEFAULT_PLOT_VARIABLE = "x"
        const val MAX_CAUSE_DEPTH = 10
        const val MAX_STACK_FRAMES = 25
    }

    private suspend fun publishVariables() {
        _variables.value = withContext(dispatcher) { engineOrNull()?.variables?.asMap().orEmpty() }
    }
}
