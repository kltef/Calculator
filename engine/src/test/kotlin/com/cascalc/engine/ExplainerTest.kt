package com.cascalc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplainerTest {

    private val engine = CasEngine()
    private val explainer = Explainer(engine)

    @Test fun `recognises a linear equation and solves it`() {
        val explanation = explainer.explain("2x + 3 = 7")!!
        assertEquals("Linear equation", explanation.headline)
        assertTrue(explanation.facts.any { it.contains("x = 2") })
    }

    @Test fun `recognises a quadratic and counts its solutions`() {
        val explanation = explainer.explain("x^2 - 4 = 0")!!
        assertEquals("Quadratic equation", explanation.headline)
        assertTrue(explanation.facts.any { it.contains("2 solutions") })
    }

    @Test fun `mentions the discriminant for a quadratic`() {
        val explanation = explainer.explain("x^2 + 1 = 0")!!
        assertTrue(explanation.facts.any { it.contains("Δ") })
    }

    @Test fun `recognises a cubic by degree`() {
        assertEquals("Cubic equation", explainer.explain("x^3 - 8 = 0")!!.headline)
    }

    @Test fun `describes plain arithmetic`() {
        val explanation = explainer.explain("6 * 7")!!
        assertEquals("Whole number", explanation.headline)
        assertTrue(explanation.facts.any { it.contains("Equals 42") })
    }

    @Test fun `reports prime factors readably, not as Symja pairs`() {
        val explanation = explainer.explain("360")!!
        val fact = explanation.facts.first { it.contains("Prime factors") }
        // 360 = 2^3 * 3^2 * 5
        assertEquals("Prime factors: 2³ × 3² × 5", fact)
        assertTrue("raw pair form leaked", !fact.contains("{"))
    }

    @Test fun `formats a factorisation with no repeated primes`() {
        assertEquals(
            "2 × 71 × 809",
            explainer.formatFactorisation("{{2,1},{71,1},{809,1}}"),
        )
    }

    @Test fun `formats a factorisation with exponents`() {
        assertEquals("2¹⁰", explainer.formatFactorisation("{{2,10}}"))
    }

    @Test fun `rejects input that is not a factorisation`() {
        assertNull(explainer.formatFactorisation("42"))
        assertNull(explainer.formatFactorisation("{{2}}"))
    }

    @Test fun `identifies a prime result`() {
        assertTrue(explainer.explain("97")!!.facts.any { it.contains("is prime") })
    }

    @Test fun `describes a fraction and its decimal`() {
        val explanation = explainer.explain("1/3")!!
        assertEquals("Fraction", explanation.headline)
        assertTrue(explanation.facts.any { it.contains("About 0.333") })
    }

    @Test fun `notes an identity rather than claiming solutions`() {
        val explanation = explainer.explain("2x = x + x")!!
        assertTrue(explanation.facts.any { it.contains("identity") })
    }

    @Test fun `returns nothing for blank input`() {
        assertNull(explainer.explain(""))
    }

    @Test fun `does not crash on nonsense`() {
        assertNotNull(explainer.explain("3 +"))
    }
}
