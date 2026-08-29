package com.cascalc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CasEngineTest {

    private val engine = CasEngine()

    private fun ok(input: String, mode: AngleMode = AngleMode.RADIANS): CalcResult.Success {
        val result = engine.evaluate(input, mode)
        assertTrue("expected success for '$input' but got $result", result is CalcResult.Success)
        return result as CalcResult.Success
    }

    private fun fail(input: String): CalcResult.Failure {
        val result = engine.evaluate(input)
        assertTrue("expected failure for '$input' but got $result", result is CalcResult.Failure)
        return result as CalcResult.Failure
    }

    // --- exact arithmetic -------------------------------------------------

    @Test fun `adds fractions exactly`() {
        assertEquals("1/2", ok("1/3 + 1/6").exact)
    }

    @Test fun `does not convert fractions to decimals prematurely`() {
        val result = ok("1/3")
        assertEquals("1/3", result.exact)
        assertEquals("0.333333333333", result.approximate)
    }

    @Test fun `reduces fractions`() {
        assertEquals("1/2", ok("2/4").exact)
    }

    @Test fun `keeps big integers exact`() {
        assertEquals("1267650600228229401496703205376", ok("2^100").exact)
    }

    @Test fun `does not lose precision on repeated division`() {
        assertEquals("1", ok("(1/3 + 1/3 + 1/3)").exact)
    }

    @Test fun `decimal input yields a decimal result`() {
        assertEquals("0.3", ok("0.1 + 0.2").exact)
    }

    @Test fun `integers report no redundant approximation`() {
        assertNull(ok("2 + 2").approximate)
    }

    // --- surds and constants ---------------------------------------------

    @Test fun `simplifies surds exactly`() {
        assertEquals("2·√(2)", ok("sqrt(8)").exact)
    }

    @Test fun `keeps pi symbolic`() {
        assertEquals("π", ok("pi").exact)
        assertEquals("3.14159265359", ok("pi").approximate)
    }

    // --- calculator notation ---------------------------------------------

    @Test fun `understands implicit multiplication`() {
        assertEquals("14", ok("2(3+4)").exact)
    }

    @Test fun `understands percent`() {
        assertEquals("30", ok("150 * 20%").exact)
    }

    @Test fun `understands unicode operators from the keypad`() {
        assertEquals("42", ok("6 × 7").exact)
    }

    // --- trigonometry -----------------------------------------------------

    @Test fun `evaluates trig exactly in radians`() {
        assertEquals("1/2", ok("sin(pi/6)").exact)
    }

    @Test fun `evaluates trig in degrees`() {
        assertEquals("1/2", ok("sin(30)", AngleMode.DEGREES).exact)
        assertEquals("0", ok("cos(90)", AngleMode.DEGREES).exact)
    }

    @Test fun `inverse trig returns degrees in degree mode`() {
        assertEquals("90", ok("asin(1)", AngleMode.DEGREES).exact)
    }

    // --- errors -----------------------------------------------------------

    @Test fun `blank input is empty, not an error`() {
        assertEquals(CalcResult.Empty, engine.evaluate("   "))
    }

    @Test fun `division by zero is reported as undefined`() {
        val failure = fail("1/0")
        assertEquals(CalcResult.ErrorKind.UNDEFINED, failure.kind)
        assertTrue(failure.message.contains("division by zero"))
    }

    @Test fun `syntax errors are reported as syntax errors`() {
        assertEquals(CalcResult.ErrorKind.SYNTAX, fail("3 + *").kind)
    }

    @Test fun `unbalanced parentheses are reported`() {
        assertEquals(CalcResult.ErrorKind.SYNTAX, fail("2 * (3 + 4").kind)
    }

    @Test fun `an unknown symbol stays symbolic rather than failing`() {
        // Groundwork for V2: `x` has no value yet, so the result is the input.
        assertEquals("1+x", ok("x + 1").exact) // Symja canonicalises term order
    }
}
