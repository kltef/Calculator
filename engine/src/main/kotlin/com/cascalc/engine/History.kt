package com.cascalc.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One completed calculation, newest entries first in [CalculationHistory]. */
data class HistoryEntry(
    val id: Long,
    val input: String,
    val result: CalcResult.Success,
    val angleMode: AngleMode,
    val timestampMillis: Long,
    val action: Action = Action.EVALUATE,
)

/**
 * Bounded, newest-first list of past calculations.
 *
 * Only successful evaluations are recorded — a live-result calculator produces
 * a failure on nearly every keystroke of a half-typed expression, and none of
 * those are worth remembering.
 */
class CalculationHistory(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var nextId = 1L
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    fun record(
        input: String,
        result: CalcResult.Success,
        angleMode: AngleMode,
        action: Action = Action.EVALUATE,
    ): HistoryEntry {
        val entry = HistoryEntry(nextId++, input.trim(), result, angleMode, clock(), action)
        _entries.value = (listOf(entry) + _entries.value).take(capacity)
        return entry
    }

    fun remove(id: Long) {
        _entries.value = _entries.value.filterNot { it.id == id }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /** Restores a persisted history (newest first); ids continue from the maximum. */
    fun restore(entries: List<HistoryEntry>) {
        _entries.value = entries.take(capacity)
        nextId = (entries.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    companion object {
        const val DEFAULT_CAPACITY = 200
    }
}
