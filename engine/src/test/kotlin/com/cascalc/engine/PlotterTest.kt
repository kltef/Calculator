package com.cascalc.engine

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotterTest {

    private val window = PlotWindow(-10.0, 10.0, -10.0, 10.0)

    @Test fun `samples a continuous function as one segment`() {
        val segments = Plotter { x -> x * x }.sample(window)
        assertEquals(1, segments.size)
        assertTrue(segments.first().size > 100)
    }

    @Test fun `splits a curve at a pole instead of drawing through it`() {
        // 1/x must not be joined across x = 0 by a vertical line.
        val segments = Plotter { x -> 1.0 / x }.sample(window)
        assertEquals(2, segments.size)
    }

    @Test fun `skips regions where the function is undefined`() {
        val segments = Plotter { x -> if (x < 0) Double.NaN else Math.sqrt(x) }.sample(window)
        assertEquals(1, segments.size)
        assertTrue(segments.first().all { it.x >= -0.1 })
    }

    @Test fun `does not split a steep but continuous climb`() {
        val segments = Plotter { x -> 50 * x }.sample(window)
        assertEquals(1, segments.size)
    }

    @Test fun `finds roots of a quadratic`() {
        val roots = Plotter { x -> x * x - 4 }.roots(window)
        assertEquals(2, roots.size)
        assertTrue(roots.any { abs(it + 2) < 1e-6 })
        assertTrue(roots.any { abs(it - 2) < 1e-6 })
    }

    @Test fun `finds no roots when the curve never crosses zero`() {
        assertTrue(Plotter { x -> x * x + 1 }.roots(window).isEmpty())
    }

    @Test fun `does not report a pole as a root`() {
        // 1/x changes sign at 0 but has no root there.
        assertTrue(Plotter { x -> 1.0 / x }.roots(window).isEmpty())
    }

    @Test fun `finds intersections of two curves`() {
        val points = Plotter.intersections({ x -> x * x }, { x -> x + 2.0 }, window)
        assertEquals(2, points.size)
        assertTrue(points.any { abs(it.x + 1) < 1e-6 })
        assertTrue(points.any { abs(it.x - 2) < 1e-6 })
    }

    @Test fun `rejects a degenerate window`() {
        assertTrue(Plotter { it }.sample(PlotWindow(1.0, 1.0, 0.0, 1.0)).isEmpty())
    }

    // --- the engine's numeric compilation --------------------------------

    @Test fun `compiles an expression into a numeric function`() {
        val engine = CasEngine()
        val f = engine.numericFunction("x^2 + 1", "x")
        assertNotNull(f)
        assertEquals(5.0, f!!(2.0), 1e-9)
    }

    @Test fun `compiled function respects angle mode`() {
        val engine = CasEngine()
        val degrees = engine.numericFunction("sin(x)", "x", AngleMode.DEGREES)!!
        assertEquals(1.0, degrees(90.0), 1e-9)
    }

    @Test fun `compiled function applies stored variables`() {
        val engine = CasEngine()
        engine.evaluate("a = 3")
        val f = engine.numericFunction("a*x", "x")!!
        assertEquals(6.0, f(2.0), 1e-9)
    }

    @Test fun `compiled function returns NaN where undefined`() {
        val engine = CasEngine()
        val f = engine.numericFunction("1/x", "x")!!
        assertTrue(f(0.0).isNaN() || f(0.0).isInfinite())
    }

    @Test fun `plot variable is inferred`() {
        val engine = CasEngine()
        assertEquals("x", engine.plotVariable("x^2"))
        assertEquals("t", engine.plotVariable("3t + 1"))
    }

    @Test fun `plotting a real function through the engine finds its roots`() {
        val engine = CasEngine()
        val f = engine.numericFunction("x^2 - 4", "x")!!
        val roots = Plotter(f).roots(window)
        assertEquals(2, roots.size)
    }
}
