package com.cascalc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgebraTest {

    private val engine = CasEngine()

    private fun ok(input: String, action: Action = Action.EVALUATE): CalcResult.Success {
        val result = engine.evaluate(input, AngleMode.RADIANS, action)
        assertTrue("expected success for '$input' ($action) but got $result", result is CalcResult.Success)
        return result as CalcResult.Success
    }

    private fun fail(input: String, action: Action = Action.EVALUATE): CalcResult.Failure {
        val result = engine.evaluate(input, AngleMode.RADIANS, action)
        assertTrue("expected failure for '$input' ($action) but got $result", result is CalcResult.Failure)
        return result as CalcResult.Failure
    }

    // --- variables --------------------------------------------------------

    @Test fun `assigns and uses a variable`() {
        assertEquals("a = 5", ok("a = 5").exact)
        assertEquals("11", ok("2a + 1").exact)
    }

    @Test fun `assignment resolves against existing variables`() {
        ok("a = 5")
        ok("b = a + 1")
        assertEquals("6", ok("b").exact)
    }

    @Test fun `redefining a variable updates later uses`() {
        ok("a = 5")
        ok("a = 10")
        assertEquals("10", ok("a").exact)
    }

    @Test fun `a variable defined from another keeps its value when the source changes`() {
        // b was defined as "a + 1" when a was 5, so b is 6 -- not a live reference.
        ok("a = 5")
        ok("b = a + 1")
        ok("a = 100")
        assertEquals("6", ok("b").exact)
    }

    @Test fun `rejects a self-referential definition`() {
        val failure = fail("a = a + 1")
        assertTrue(failure.message.contains("itself"))
    }

    @Test fun `rejects assigning to a built-in name`() {
        assertTrue(fail("pi = 3").message.contains("built-in"))
        assertTrue(fail("sin = 3").message.contains("built-in"))
    }

    @Test fun `variables are exact, not decimal`() {
        ok("third = 1/3")
        assertEquals("1", ok("3 * third").exact)
    }

    @Test fun `clearing a variable makes it symbolic again`() {
        ok("a = 5")
        engine.clearVariable("a")
        assertEquals("1+a", ok("a + 1").exact)
    }

    @Test fun `reset forgets every variable`() {
        ok("a = 5")
        engine.reset()
        assertTrue(engine.variables.asMap().isEmpty())
    }

    // --- simplify / expand / factor ---------------------------------------

    @Test fun `expands a binomial`() {
        assertEquals("1+2·x+x^2", ok("(x+1)^2", Action.EXPAND).exact)
    }

    @Test fun `factors a difference of squares`() {
        assertEquals("(-2+x)·(2+x)", ok("x^2 - 4", Action.FACTOR).exact)
    }

    @Test fun `simplifies a cancelling fraction`() {
        assertEquals("1+x", ok("(x^2 - 1)/(x - 1)", Action.SIMPLIFY).exact)
    }

    @Test fun `simplify applies variable bindings first`() {
        ok("n = 2")
        assertEquals("1+2·x+x^2", ok("(x+n-1)^2", Action.EXPAND).exact)
    }

    // --- solving ----------------------------------------------------------

    @Test fun `solves a linear equation`() {
        val result = ok("2x + 3 = 7", Action.SOLVE)
        assertEquals("x = 2", result.exact)
        assertEquals("Solved for x", result.note)
    }

    @Test fun `solves a quadratic with two roots`() {
        assertEquals("x = -2,  x = 2", ok("x^2 - 4 = 0", Action.SOLVE).exact)
    }

    @Test fun `an expression with no equals sign is solved against zero`() {
        assertEquals("x = -2,  x = 2", ok("x^2 - 4", Action.SOLVE).exact)
    }

    @Test fun `solves for the only variable present`() {
        val result = ok("3t - 9 = 0", Action.SOLVE)
        assertEquals("t = 3", result.exact)
        assertEquals("Solved for t", result.note)
    }

    @Test fun `prefers x when several variables are present`() {
        assertEquals("Solved for x", ok("x + y = 0", Action.SOLVE).note)
    }

    @Test fun `substitutes known variables before solving`() {
        ok("b = 3")
        assertEquals("x = -3", ok("x + b = 0", Action.SOLVE).exact)
    }

    @Test fun `reports when there is nothing to solve for`() {
        assertTrue(fail("2 + 2 = 4", Action.SOLVE).message.contains("no unknown"))
    }

    @Test fun `reports an equation that is always true`() {
        val result = ok("2x = x + x", Action.SOLVE)
        assertEquals("Any value of x", result.exact)
        assertTrue(result.note!!.contains("every x"))
    }

    @Test fun `reports an equation with no solution`() {
        assertEquals("No solution", fail("x + 1 = x + 2", Action.SOLVE).message)
    }

    // --- step by step -----------------------------------------------------

    @Test fun `shows steps for a linear equation`() {
        val steps = ok("2x + 3 = 7", Action.SOLVE).steps
        assertEquals(3, steps.size)
        assertTrue(steps[0].explanation.contains("Move every term"))
        assertEquals("x = 2", steps.last().expression)
    }

    @Test fun `shows steps for a quadratic equation`() {
        val steps = ok("x^2 - 5x + 6 = 0", Action.SOLVE).steps
        assertTrue(steps.any { it.explanation.contains("discriminant") })
        assertTrue(steps.any { it.expression == "Δ = 1" })
        assertEquals("x = 2    x = 3", steps.last().expression)
    }

    @Test fun `names the discriminant case for a repeated root`() {
        val steps = ok("x^2 - 2x + 1 = 0", Action.SOLVE).steps
        assertTrue(steps.any { it.explanation.contains("repeated root") })
        assertEquals("x = 1", steps.last().expression)
    }

    @Test fun `names the discriminant case when there are no real roots`() {
        val steps = ok("x^2 + 1 = 0", Action.SOLVE).steps
        assertTrue(steps.any { it.explanation.contains("no real solutions") })
    }

    @Test fun `offers no steps for equations outside the taught methods`() {
        // Still solved -- just without a fabricated derivation.
        val result = ok("x^3 - 8 = 0", Action.SOLVE)
        assertTrue(result.exact.contains("x = 2"))
        assertTrue(result.steps.isEmpty())
    }

    @Test fun `offers no steps for a non-polynomial equation`() {
        assertTrue(ok("sin(x) = 0", Action.SOLVE).steps.isEmpty())
    }

    // --- V1 behaviour still intact ----------------------------------------

    @Test fun `plain arithmetic is unaffected`() {
        assertEquals("1/2", ok("1/3 + 1/6").exact)
    }

    @Test fun `an equation evaluates to a truth value`() {
        assertEquals("True", ok("2 + 3 = 5").exact)
    }
}
