package com.cascalc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculusTest {

    private val engine = CasEngine()

    private fun ok(input: String, action: Action = Action.EVALUATE): CalcResult.Success {
        val result = engine.evaluate(input, AngleMode.RADIANS, action)
        assertTrue("expected success for '$input' ($action) but got $result", result is CalcResult.Success)
        return result as CalcResult.Success
    }

    // --- derivatives ------------------------------------------------------

    @Test fun `differentiates a polynomial`() {
        assertEquals("2·x", ok("x^2", Action.DIFFERENTIATE).exact)
    }

    @Test fun `differentiates a product`() {
        assertEquals("x·Cos(x)+Sin(x)", ok("x*sin(x)", Action.DIFFERENTIATE).exact)
    }

    @Test fun `infers the variable when it is not x`() {
        val result = ok("3t^2", Action.DIFFERENTIATE)
        assertEquals("6·t", result.exact)
        assertTrue(result.note!!.contains("respect to t"))
    }

    @Test fun `typed derivative syntax works`() {
        assertEquals("2·x", ok("d(x^2, x)").exact)
    }

    @Test fun `higher derivatives are available by typed syntax`() {
        assertEquals("6·x", ok("d(x^3, {x, 2})").exact)
    }

    // --- integrals --------------------------------------------------------

    @Test fun `finds an antiderivative`() {
        assertEquals("x^3/3", ok("x^2", Action.INTEGRATE).exact)
    }

    @Test fun `notes the constant of integration`() {
        assertTrue(ok("x^2", Action.INTEGRATE).note!!.contains("C"))
    }

    @Test fun `evaluates a definite integral exactly`() {
        assertEquals("1/3", ok("integrate(x^2, {x, 0, 1})").exact)
    }

    @Test fun `definite integral of a transcendental function`() {
        assertEquals("2", ok("integrate(sin(x), {x, 0, pi})").exact)
    }

    // --- limits -----------------------------------------------------------

    @Test fun `computes a limit at zero`() {
        assertEquals("1", ok("sin(x)/x", Action.LIMIT).exact)
    }

    @Test fun `computes a limit at infinity by typed syntax`() {
        assertEquals("E", ok("limit((1+1/n)^n, n -> Infinity)").exact)
    }

    // --- steps ------------------------------------------------------------

    @Test fun `shows power-rule steps for a polynomial derivative`() {
        val steps = ok("x^3 + 2x^2 + 5", Action.DIFFERENTIATE).steps
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.first().explanation.contains("power rule"))
        // d/dx (x^3 + 2x^2 + 5) = 3x^2 + 4x
        assertEquals("4·x+3·x^2", steps.last().expression)
    }

    @Test fun `offers no derivative steps for a non-polynomial`() {
        assertTrue(ok("sin(x)", Action.DIFFERENTIATE).steps.isEmpty())
    }

    @Test fun `reports when there is no variable to differentiate`() {
        val result = engine.evaluate("2 + 2", AngleMode.RADIANS, Action.DIFFERENTIATE)
        assertTrue(result is CalcResult.Failure)
        assertTrue((result as CalcResult.Failure).message.contains("no variable"))
    }
}
