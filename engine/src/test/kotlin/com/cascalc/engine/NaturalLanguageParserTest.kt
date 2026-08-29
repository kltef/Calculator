package com.cascalc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalLanguageParserTest {

    private fun parsed(input: String): String {
        val result = NaturalLanguageParser.parse(input)
        assertTrue("not understood: '$input'", result is NaturalLanguageParser.Result.Parsed)
        return (result as NaturalLanguageParser.Result.Parsed).expression
    }

    private fun evaluates(input: String): String {
        val engine = CasEngine()
        return (engine.evaluate(parsed(input)) as CalcResult.Success).exact
    }

    @Test fun `handles percent-of, the roadmap's own example`() {
        assertEquals("30", evaluates("what's 20 percent of 150"))
        assertEquals("30", evaluates("what's 20% of 150"))
    }

    @Test fun `refuses the part of that example with no defined value`() {
        // "plus tax" names no rate, so there is no honest answer. Guessing a
        // number here would be worse than saying so.
        assertEquals(
            NaturalLanguageParser.Result.NotUnderstood,
            NaturalLanguageParser.parse("what's 20% of 150 plus tax"),
        )
    }

    @Test fun `understands spoken operators`() {
        assertEquals("12", evaluates("six times two"))
        assertEquals("8", evaluates("what is 5 plus 3"))
        assertEquals("4", evaluates("twelve divided by three"))
    }

    @Test fun `understands spelled-out compound numbers`() {
        assertEquals("25", parsed("twenty five"))
        assertEquals("250", parsed("two hundred and fifty"))
        assertEquals("1500", parsed("one thousand five hundred"))
    }

    @Test fun `understands powers and roots`() {
        assertEquals("25", evaluates("five squared"))
        assertEquals("3", evaluates("square root of nine"))
        assertEquals("8", evaluates("two to the power of three"))
    }

    @Test fun `strips question phrasing and punctuation`() {
        assertEquals("4", evaluates("what's 2 plus 2?"))
        assertEquals("4", evaluates("calculate 2 plus 2"))
    }

    @Test fun `does not mistake a joining and for addition`() {
        assertEquals("205", parsed("two hundred and five"))
    }

    @Test fun `reports honestly when it does not understand`() {
        assertEquals(
            NaturalLanguageParser.Result.NotUnderstood,
            NaturalLanguageParser.parse("what is the airspeed velocity of a swallow"),
        )
        assertEquals(
            NaturalLanguageParser.Result.NotUnderstood,
            NaturalLanguageParser.parse(""),
        )
    }
}
