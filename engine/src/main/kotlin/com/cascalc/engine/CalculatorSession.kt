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
            null
        }
    }

    private fun startupFailureResult(): CalcResult {
        val failure = startupFailure
        val detail = failure?.let { "${it::class.java.simpleName}: ${it.message}" }.orEmpty()
        return CalcResult.Failure(
            CalcResult.ErrorKind.INTERNAL,
            "The math engine couldn't start. $detail".trim(),
        )
    }

    private suspend fun publishVariables() {
        _variables.value = withContext(dispatcher) { engineOrNull()?.variables?.asMap().orEmpty() }
    }
}
