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
 */
class CalculatorSession(
    private val engine: CasEngine = CasEngine(),
    val history: CalculationHistory = CalculationHistory(),
) : AutoCloseable {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "cas-engine").apply { isDaemon = true }
    }
    private val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    private val _variables = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The user's variable bindings, for display. */
    val variables: StateFlow<Map<String, String>> = _variables.asStateFlow()

    /** Evaluates without touching history — used for the live preview as you type. */
    suspend fun preview(
        input: String,
        angleMode: AngleMode,
        action: Action = Action.EVALUATE,
    ): CalcResult = withContext(dispatcher) { engine.evaluate(input, angleMode, action) }

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
        withContext(dispatcher) { engine.clearVariable(name) }
        publishVariables()
    }

    suspend fun reset() {
        withContext(dispatcher) { engine.reset() }
        history.clear()
        publishVariables()
    }

    private suspend fun publishVariables() {
        _variables.value = withContext(dispatcher) { engine.variables.asMap() }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
