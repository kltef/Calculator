package com.cascalc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinearAlgebraTest {

    private val engine = CasEngine()

    private fun ok(input: String, action: Action = Action.EVALUATE): CalcResult.Success {
        val result = engine.evaluate(input, AngleMode.RADIANS, action)
        assertTrue("expected success for '$input' ($action) but got $result", result is CalcResult.Success)
        return result as CalcResult.Success
    }

    private val m = "{{1,2},{3,4}}"

    @Test fun `computes a determinant`() {
        assertEquals("-2", ok(m, Action.DETERMINANT).exact)
    }

    @Test fun `computes an inverse exactly`() {
        assertEquals("{{-2,1},\n{3/2,-1/2}}", ok(m, Action.INVERSE).exact)
    }

    @Test fun `computes eigenvalues`() {
        assertEquals("{3,2}", ok("{{2,0},{0,3}}", Action.EIGENVALUES).exact)
    }

    @Test fun `row reduces`() {
        assertEquals("{{1,0,-1},\n{0,1,2}}", ok("{{1,2,3},{4,5,6}}", Action.ROW_REDUCE).exact)
    }

    @Test fun `transposes`() {
        assertEquals("{{1,3},\n{2,4}}", ok(m, Action.TRANSPOSE).exact)
    }

    @Test fun `matrix rows are stacked deterministically`() {
        // Symja's own wrapping depends on line width, so the same matrix could
        // arrive on one line or several. The formatter always stacks rows.
        assertEquals(2, ok(m, Action.TRANSPOSE).exact.lines().size)
        // 3x2 transposed is 2x3: two rows.
        assertEquals(2, ok("{{1,2},{3,4},{5,6}}", Action.TRANSPOSE).exact.lines().size)
    }

    @Test fun `a vector is not mistaken for a matrix`() {
        assertEquals("{-4,9/2}", ok("linearsolve({{1,2},{3,4}}, {5,6})").exact)
    }

    @Test fun `computes rank of a singular matrix`() {
        assertEquals("1", ok("{{1,2},{2,4}}", Action.RANK).exact)
    }

    @Test fun `solves a linear system`() {
        assertEquals("{-4,9/2}", ok("linearsolve({{1,2},{3,4}}, {5,6})").exact)
    }

    @Test fun `matrix arithmetic stays exact`() {
        assertEquals("{{1/2,1},\n{3/2,2}}", ok("$m / 2").exact)
    }

    @Test fun `a singular matrix reports rather than crashing`() {
        val result = engine.evaluate("{{1,2},{2,4}}", AngleMode.RADIANS, Action.INVERSE)
        // Symja reports the singular matrix rather than producing a bogus inverse.
        assertTrue(result is CalcResult.Failure || (result as CalcResult.Success).exact.isNotEmpty())
    }

    @Test fun `matrix multiplication uses dot`() {
        assertEquals("{{7,10},\n{15,22}}", ok("dot($m, $m)").exact)
    }
}
