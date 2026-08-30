package com.cascalc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplainerTest {

    private val engine = CasEngine()
    private val explainer = Explainer(engine)

    private fun explain(input: String) = explainer.explain(input)!!

    private fun allLines(input: String): List<String> =
        explain(input).sections.flatMap { it.lines }

    private fun section(input: String, title: String): List<String> =
        explain(input).sections.first { it.title == title }.lines

    // --- the user's example: explain what the symbols do -------------------

    @Test fun `explains what plus does for 1 + 1`() {
        val explanation = explain("1 + 1")
        assertEquals("An addition", explanation.headline)
        assertEquals("1 plus 1", explanation.reading)

        val symbols = explanation.sections.first { it.title == "What the symbols mean" }.lines
        assertEquals(1, symbols.size)
        assertTrue(symbols.single().contains("plus"))
        assertTrue(symbols.single().contains("Adds the two amounts together"))
        assertTrue(symbols.single().contains("e.g."))
    }

    @Test fun `gives the answer as well as the meaning`() {
        assertTrue(section("1 + 1", "The answer").any { it.contains("= 2") })
    }

    @Test fun `explains multiplication in terms of repeated addition`() {
        val symbols = section("3 * 4", "What the symbols mean")
        assertTrue(symbols.single().contains("four lots of 3") ||
            symbols.single().contains("repeatedly"))
    }

    @Test fun `explains powers`() {
        assertTrue(section("2^3", "What the symbols mean").single().contains("by itself"))
    }

    // --- precedence, the thing that makes answers surprising ---------------

    @Test fun `explains that times happens before plus`() {
        val order = section("1+2*8*9", "Which part happens first")
        assertTrue(order.any { it.contains("* is worked out before +") })
        assertTrue(order.any { it.contains("Brackets") })
    }

    @Test fun `no ordering note when every operator is equal strength`() {
        assertTrue(explain("1 + 2 + 3").sections.none { it.title == "Which part happens first" })
    }

    @Test fun `the mixed calculation is still answered correctly`() {
        assertTrue(section("1+2*8*9", "The answer").any { it.contains("= 145") })
    }

    // --- reading aloud -----------------------------------------------------

    @Test fun `reads an expression as words`() {
        assertEquals("1 plus 2 times 8", explain("1 + 2*8").reading)
    }

    @Test fun `reads a function by its name`() {
        assertTrue(explain("sqrt(9)").reading!!.contains("square root"))
    }

    // --- equations ---------------------------------------------------------

    @Test fun `recognises a linear equation and shows the working`() {
        val explanation = explain("2x + 3 = 7")
        assertEquals("Linear equation", explanation.headline)
        assertTrue(section("2x + 3 = 7", "The answer").any { it.contains("x = 2") })
        assertTrue(explanation.sections.any { it.title == "How it is solved" })
    }

    @Test fun `explains what equals means`() {
        assertTrue(
            section("2x + 3 = 7", "What the symbols mean")
                .any { it.contains("same value") },
        )
    }

    @Test fun `recognises a quadratic and counts its solutions`() {
        val explanation = explain("x^2 - 4 = 0")
        assertEquals("Quadratic equation", explanation.headline)
        assertTrue(section("x^2 - 4 = 0", "The answer").any { it.contains("2 values") })
    }

    @Test fun `names an identity rather than claiming solutions`() {
        assertTrue(allLines("2x = x + x").any { it.contains("identity") })
    }

    // --- numbers worth noticing -------------------------------------------

    @Test fun `explains a prime result`() {
        assertTrue(allLines("97").any { it.contains("is prime") })
    }

    @Test fun `breaks a composite number into primes readably`() {
        val line = allLines("360").first { it.contains("primes") }
        assertEquals("Breaks into primes as 2³ × 3² × 5", line)
        assertTrue("raw pair form leaked", !line.contains("{"))
    }

    @Test fun `points out that a fraction is exact`() {
        assertTrue(allLines("1/3").any { it.contains("exact fraction") })
        assertTrue(allLines("1/3").any { it.contains("0.333") })
    }

    // --- formatting helper -------------------------------------------------

    @Test fun `formats a factorisation with no repeated primes`() {
        assertEquals("2 × 71 × 809", explainer.formatFactorisation("{{2,1},{71,1},{809,1}}"))
    }

    @Test fun `formats a factorisation with exponents`() {
        assertEquals("2¹⁰", explainer.formatFactorisation("{{2,10}}"))
    }

    @Test fun `rejects input that is not a factorisation`() {
        assertNull(explainer.formatFactorisation("42"))
        assertNull(explainer.formatFactorisation("{{2}}"))
    }

    // --- robustness --------------------------------------------------------

    @Test fun `returns nothing for blank input`() {
        assertNull(explainer.explain(""))
    }

    @Test fun `says so rather than crashing on nonsense`() {
        val explanation = explainer.explain("3 +")
        assertNotNull(explanation)
        assertTrue(explanation!!.sections.any { section -> section.lines.any { it.contains("can't be worked out") } })
    }

    @Test fun `plain text rendering includes the headline and sections`() {
        val text = explain("1 + 1").plainText
        assertTrue(text.startsWith("An addition"))
        assertTrue(text.contains("What the symbols mean"))
    }
}
