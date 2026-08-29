package com.cascalc.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphExtrasTest {

    private val engine = CasEngine()
    private val window = PlotWindow(-10.0, 10.0, -10.0, 10.0)

    // --- tangent lines (V4 on V3's graphs) --------------------------------

    @Test fun `tangent to a parabola uses the exact derivative`() {
        // f(x) = x^2 at x = 3: slope 6, line y = 6x - 9.
        val tangent = engine.tangentAt("x^2", "x", 3.0)!!
        assertEquals(6.0, tangent.slope, 1e-9)
        assertEquals(-9.0, tangent.intercept, 1e-9)
        assertEquals(9.0, tangent.at.y, 1e-9)
    }

    @Test fun `tangent touches the curve at the point`() {
        val tangent = engine.tangentAt("sin(x)", "x", 0.5)!!
        val f = engine.numericFunction("sin(x)", "x")!!
        assertEquals(f(0.5), tangent.valueAt(0.5), 1e-9)
    }

    @Test fun `tangent slope of sine is cosine`() {
        val tangent = engine.tangentAt("sin(x)", "x", 1.0)!!
        assertEquals(cos(1.0), tangent.slope, 1e-7)
    }

    @Test fun `tangent is flat at a turning point`() {
        assertEquals(0.0, engine.tangentAt("x^2", "x", 0.0)!!.slope, 1e-9)
    }

    @Test fun `no tangent where the function is undefined`() {
        assertNull(engine.tangentAt("1/x", "x", 0.0))
    }

    // --- area under a curve ----------------------------------------------

    @Test fun `area under a parabola matches the exact integral`() {
        // integral of x^2 from 0 to 3 = 9
        val area = Plotter { x -> x * x }.areaUnder(0.0, 3.0)!!
        assertEquals(9.0, area.area, 1e-6)
        assertTrue(area.points.size > 10)
    }

    @Test fun `area is signed, so below the axis is negative`() {
        val area = Plotter { x -> -1.0 }.areaUnder(0.0, 2.0)!!
        assertEquals(-2.0, area.area, 1e-9)
    }

    @Test fun `area of a half sine wave`() {
        val area = Plotter { x -> sin(x) }.areaUnder(0.0, PI)!!
        assertEquals(2.0, area.area, 1e-6)
    }

    @Test fun `area rejects a reversed interval`() {
        assertNull(Plotter { it }.areaUnder(3.0, 1.0))
    }

    @Test fun `exact definite integral is preferred where one exists`() {
        // Bounds must not be passed as floats, or the exact 9 becomes 9.0.
        assertEquals("9", engine.definiteIntegral("x^2", "x", 0.0, 3.0))
        assertEquals("1/3", engine.definiteIntegral("x^2", "x", 0.0, 1.0))
    }

    @Test fun `fractional bounds stay exact too`() {
        assertEquals("1/24", engine.definiteIntegral("x^2", "x", 0.0, 0.5))
    }

    @Test fun `returns null when there is no closed form`() {
        assertNull(engine.definiteIntegral("sin(x^x)", "x", 0.0, 1.0))
    }

    // --- polar and parametric --------------------------------------------

    @Test fun `polar circle has constant radius`() {
        val segments = Plotter { 5.0 }.samplePolar()
        val points = segments.flatten()
        assertTrue(points.isNotEmpty())
        assertTrue(points.all { abs(Math.hypot(it.x, it.y) - 5.0) < 1e-6 })
    }

    @Test fun `polar rose keeps negative radii`() {
        // r = cos(2t) sweeps to negative r, which must be plotted opposite the
        // angle rather than clamped away.
        val points = Plotter { t -> cos(2 * t) }.samplePolar().flatten()
        assertTrue(points.any { it.x < -0.5 })
    }

    @Test fun `parametric circle closes`() {
        val segments = Plotter { t -> cos(t) }
            .sampleParametric({ t -> sin(t) }, 0.0, 2 * PI)
        val points = segments.flatten()
        assertTrue(points.all { abs(Math.hypot(it.x, it.y) - 1.0) < 1e-6 })
    }

    @Test fun `parametric breaks where undefined`() {
        val segments = Plotter { t -> t }
            .sampleParametric({ t -> if (abs(t) < 0.1) Double.NaN else 1.0 / t }, -1.0, 1.0)
        assertTrue(segments.size >= 2)
    }

    @Test fun `graphing the derivative of a function works end to end`() {
        val derivative = (engine.evaluate("x^3", AngleMode.RADIANS, Action.DIFFERENTIATE)
            as CalcResult.Success).raw
        val f = engine.numericFunction(derivative, "x")!!
        assertEquals(12.0, f(2.0), 1e-9) // d/dx x^3 = 3x^2 -> 12 at x=2
    }
}
