package com.cascalc.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorSessionTest {

    @Test fun `submit records successful calculations`() = runBlocking {
        CalculatorSession().use { session ->
            session.submit("1/3 + 1/6", AngleMode.RADIANS)

            val entries = session.history.entries.value
            assertEquals(1, entries.size)
            assertEquals("1/3 + 1/6", entries.first().input)
            assertEquals("1/2", entries.first().result.exact)
        }
    }

    @Test fun `submit does not record failures`() = runBlocking {
        CalculatorSession().use { session ->
            session.submit("3 + *", AngleMode.RADIANS)
            assertTrue(session.history.entries.value.isEmpty())
        }
    }

    @Test fun `preview never records`() = runBlocking {
        CalculatorSession().use { session ->
            val result = session.preview("2 + 2", AngleMode.RADIANS)
            assertEquals("4", (result as CalcResult.Success).exact)
            assertTrue(session.history.entries.value.isEmpty())
        }
    }

    @Test fun `reset clears history`() = runBlocking {
        CalculatorSession().use { session ->
            session.submit("2 + 2", AngleMode.RADIANS)
            session.reset()
            assertTrue(session.history.entries.value.isEmpty())
        }
    }
}
