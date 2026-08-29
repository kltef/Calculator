package com.cascalc.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
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

    /** Evaluates without touching history — used for the live preview as you type. */
    suspend fun preview(input: String, angleMode: AngleMode): CalcResult =
        withContext(dispatcher) { engine.evaluate(input, angleMode) }

    /** Evaluates and, on success, records the calculation in [history]. */
    suspend fun submit(input: String, angleMode: AngleMode): CalcResult {
        val result = preview(input, angleMode)
        if (result is CalcResult.Success) history.record(input, result, angleMode)
        return result
    }

    suspend fun reset() {
        withContext(dispatcher) { engine.reset() }
        history.clear()
    }

    override fun close() {
        executor.shutdownNow()
    }
}
