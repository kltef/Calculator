package com.cascalc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculationHistoryTest {

    private fun success(text: String) = CalcResult.Success(text, null, text)

    @Test fun `records newest first`() {
        val history = CalculationHistory()
        history.record("1+1", success("2"), AngleMode.RADIANS)
        history.record("2+2", success("4"), AngleMode.RADIANS)

        assertEquals(listOf("2+2", "1+1"), history.entries.value.map { it.input })
    }

    @Test fun `assigns unique ids`() {
        val history = CalculationHistory()
        val a = history.record("1+1", success("2"), AngleMode.RADIANS)
        val b = history.record("2+2", success("4"), AngleMode.RADIANS)
        assertTrue(a.id != b.id)
    }

    @Test fun `drops oldest beyond capacity`() {
        val history = CalculationHistory(capacity = 2)
        repeat(5) { history.record("$it", success("$it"), AngleMode.RADIANS) }

        assertEquals(2, history.entries.value.size)
        assertEquals(listOf("4", "3"), history.entries.value.map { it.input })
    }

    @Test fun `removes a single entry`() {
        val history = CalculationHistory()
        val first = history.record("1+1", success("2"), AngleMode.RADIANS)
        history.record("2+2", success("4"), AngleMode.RADIANS)

        history.remove(first.id)
        assertEquals(listOf("2+2"), history.entries.value.map { it.input })
    }

    @Test fun `clears everything`() {
        val history = CalculationHistory()
        history.record("1+1", success("2"), AngleMode.RADIANS)
        history.clear()
        assertTrue(history.entries.value.isEmpty())
    }

    @Test fun `restore continues id sequence`() {
        val history = CalculationHistory()
        history.restore(
            listOf(HistoryEntry(7, "1+1", success("2"), AngleMode.RADIANS, 0L)),
        )
        val next = history.record("2+2", success("4"), AngleMode.RADIANS)
        assertEquals(8L, next.id)
    }

    @Test fun `trims input whitespace when recording`() {
        val history = CalculationHistory()
        val entry = history.record("  1+1  ", success("2"), AngleMode.RADIANS)
        assertEquals("1+1", entry.input)
    }
}
