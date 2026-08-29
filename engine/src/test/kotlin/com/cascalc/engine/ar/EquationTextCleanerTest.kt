package com.cascalc.engine.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EquationTextCleanerTest {

    @Test fun `keeps a plain equation`() {
        assertEquals("2+2", EquationTextCleaner.clean("2+2"))
    }

    @Test fun `normalises handwriting symbols`() {
        assertEquals("6*7", EquationTextCleaner.clean("6×7"))
        assertEquals("6/7", EquationTextCleaner.clean("6÷7"))
        assertEquals("6-7", EquationTextCleaner.clean("6−7"))
        assertEquals("x^2+1", EquationTextCleaner.clean("x²+1"))
    }

    @Test fun `drops a trailing equals sign`() {
        assertEquals("2+2", EquationTextCleaner.clean("2+2="))
        // Internal spacing is left alone; the input normalizer handles it.
        assertEquals("2 + 2", EquationTextCleaner.clean("2 + 2 = "))
    }

    @Test fun `fixes letter-digit confusions next to numbers`() {
        assertEquals("10+5", EquationTextCleaner.clean("1O+5"))
        assertEquals("1+1", EquationTextCleaner.clean("l+1"))
    }

    @Test fun `does not corrupt function names`() {
        // The 'l' and 'o' in log are not next to digits or operators.
        assertEquals("log(100)+1", EquationTextCleaner.clean("log(100)+1"))
        assertEquals("cos(0)+1", EquationTextCleaner.clean("cos(0)+1"))
    }

    @Test fun `rejects prose`() {
        assertNull(EquationTextCleaner.clean("buy 2 pints of milk and bread"))
        assertNull(EquationTextCleaner.clean("Chapter 4"))
        assertNull(EquationTextCleaner.clean("hello world"))
    }

    @Test fun `rejects text with no operator`() {
        assertNull(EquationTextCleaner.clean("2024"))
    }

    @Test fun `rejects text with no digits`() {
        assertNull(EquationTextCleaner.clean("x + y ="))
    }

    @Test fun `rejects empty input`() {
        assertNull(EquationTextCleaner.clean(""))
        assertNull(EquationTextCleaner.clean("   "))
    }

    @Test fun `accepts algebra with a variable`() {
        assertEquals("2x+5=11", EquationTextCleaner.clean("2x+5=11"))
    }
}
