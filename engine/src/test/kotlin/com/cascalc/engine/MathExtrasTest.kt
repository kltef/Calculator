package com.cascalc.engine

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathExtrasTest {

    private val engine = CasEngine()

    private fun exact(input: String) =
        (engine.evaluate(input) as CalcResult.Success).exact

    // --- units ------------------------------------------------------------

    @Test fun `converts length`() {
        val result = Units.convert(1.0, "mile", "km") as Units.Result.Converted
        assertEquals(1.609344, result.value, 1e-9)
    }

    @Test fun `converts by alias and is case-insensitive`() {
        val result = Units.convert(1.0, "MI", "Kilometres") as Units.Result.Converted
        assertEquals(1.609344, result.value, 1e-9)
    }

    @Test fun `converts temperature using its offset, not a ratio`() {
        val boiling = Units.convert(100.0, "celsius", "fahrenheit") as Units.Result.Converted
        assertEquals(212.0, boiling.value, 1e-9)
        val freezing = Units.convert(32.0, "fahrenheit", "celsius") as Units.Result.Converted
        assertEquals(0.0, freezing.value, 1e-9)
    }

    @Test fun `zero celsius is not zero fahrenheit`() {
        val result = Units.convert(0.0, "celsius", "fahrenheit") as Units.Result.Converted
        assertEquals(32.0, result.value, 1e-9)
    }

    @Test fun `refuses to convert between different dimensions`() {
        assertTrue(Units.convert(1.0, "metre", "gram") is Units.Result.Mismatched)
    }

    @Test fun `reports an unknown unit`() {
        assertTrue(Units.convert(1.0, "metre", "furlong") is Units.Result.Unknown)
    }

    @Test fun `offers peer units of the same dimension`() {
        val metre = Units.find("m")!!
        assertTrue(Units.peersOf(metre).any { it.name == "mile" })
        assertTrue(Units.peersOf(metre).none { it.dimension == "mass" })
    }

    // --- constants --------------------------------------------------------

    @Test fun `constants evaluate through the engine`() {
        val c = Constants.find("c")!!
        assertEquals("299792458", exact(c.expression))
    }

    @Test fun `golden ratio is exact`() {
        val phi = Constants.find("φ")!!
        assertEquals("1/2·(1+√(5))", exact(phi.expression))
    }

    @Test fun `every constant is a valid expression`() {
        for (constant in Constants.ALL) {
            val result = engine.evaluate(constant.expression)
            assertTrue("${constant.name} failed: $result", result is CalcResult.Success)
        }
    }

    // --- base conversion --------------------------------------------------

    @Test fun `converts decimal to hex and binary`() {
        assertEquals("FF", (BaseConverter.formatDecimal("255", 16) as BaseConverter.Result.Converted).text)
        assertEquals("11111111", (BaseConverter.formatDecimal("255", 2) as BaseConverter.Result.Converted).text)
    }

    @Test fun `parses hex back to decimal`() {
        assertEquals("255", (BaseConverter.parse("ff", 16) as BaseConverter.Result.Converted).text)
        assertEquals("255", (BaseConverter.parse("0xff", 16) as BaseConverter.Result.Converted).text)
    }

    @Test fun `handles integers too large for a Long`() {
        val big = BigInteger.TWO.pow(100)
        val hex = BaseConverter.format(big, 16) as BaseConverter.Result.Converted
        assertEquals("255", (BaseConverter.parse("FF", 16) as BaseConverter.Result.Converted).text)
        assertEquals(big, BigInteger(hex.text, 16))
    }

    @Test fun `rejects an out-of-range base`() {
        assertTrue(BaseConverter.formatDecimal("10", 99) is BaseConverter.Result.BadBase)
    }

    @Test fun `rejects digits that are invalid in the base`() {
        assertTrue(BaseConverter.parse("129", 2) is BaseConverter.Result.BadDigits)
    }

    // --- number theory and statistics (via Symja) -------------------------

    @Test fun `number theory functions work by their common names`() {
        assertEquals("True", exact("isprime(97)"))
        assertEquals("101", exact("nextprime(100)"))
        assertEquals("{1,2,4,7,14,28}", exact("divisors(28)"))
        assertEquals("{{2,3},\n{3,2},\n{5,1}}", exact("factorint(360)"))
    }

    @Test fun `statistics stay exact`() {
        assertEquals("5/2", exact("mean({1,2,3,4})"))
        assertEquals("5/3", exact("variance({1,2,3,4})"))
        assertEquals("√(5/3)", exact("sd({1,2,3,4})"))
    }
}
