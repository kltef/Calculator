package com.cascalc.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class InputNormalizerTest {

    private fun norm(input: String, mode: AngleMode = AngleMode.RADIANS) =
        InputNormalizer.normalize(input, mode)

    @Test fun `maps unicode operators`() {
        assertEquals("6*7", norm("6 × 7"))
        assertEquals("6/7", norm("6 ÷ 7"))
        assertEquals("6-7", norm("6 − 7"))
    }

    @Test fun `maps function names to symja spelling`() {
        assertEquals("Sin(0)", norm("sin(0)"))
        assertEquals("Log10(100)", norm("log(100)"))
        assertEquals("Log(1)", norm("ln(1)"))
        assertEquals("Sqrt(9)", norm("sqrt(9)"))
    }

    @Test fun `maps constants`() {
        assertEquals("π", ResultFormatter.prettify(norm("pi")))
        assertEquals("Pi", norm("PI"))
        assertEquals("E", norm("e"))
    }

    @Test fun `expands bare square root`() {
        assertEquals("Sqrt(9)", norm("√9"))
        assertEquals("Sqrt(9)", norm("sqrt 9"))
        assertEquals("Sqrt(1+3)", norm("√(1+3)"))
    }

    @Test fun `inserts implicit multiplication`() {
        assertEquals("2*(3+4)", norm("2(3+4)"))
        assertEquals("(1+2)*(3+4)", norm("(1+2)(3+4)"))
        assertEquals("2*x", norm("2x"))
        assertEquals("2*Pi", norm("2pi"))
        assertEquals("2*Sqrt(2)", norm("2sqrt(2)"))
    }

    @Test fun `does not break function calls`() {
        assertEquals("Sin(2*Pi)", norm("sin(2pi)"))
        assertEquals("Max(1,2)", norm("max(1,2)"))
    }

    @Test fun `multiplies adjacent numbers rather than joining them`() {
        // `20/100 150` must not become `20/100150`.
        assertEquals("2*3", norm("2 3"))
        assertEquals("20/100*150", norm("20% 150"))
    }

    @Test fun `expands postfix percent`() {
        assertEquals("20/100", norm("20%"))
        assertEquals("150*20/100", norm("150*20%"))
    }

    @Test fun `degree mode wraps whole trig argument`() {
        assertEquals("Sin((30)*Degree)", norm("sin(30)", AngleMode.DEGREES))
        assertEquals("Sin((10+20)*Degree)", norm("sin(10+20)", AngleMode.DEGREES))
    }

    @Test fun `degree mode converts inverse trig results back`() {
        assertEquals("(ArcSin(1))/Degree", norm("asin(1)", AngleMode.DEGREES))
    }

    @Test fun `degree mode handles nesting`() {
        assertEquals("Sin((Cos((0)*Degree))*Degree)", norm("sin(cos(0))", AngleMode.DEGREES))
    }

    @Test fun `radian mode leaves trig untouched`() {
        assertEquals("Sin(30)", norm("sin(30)"))
    }
}
