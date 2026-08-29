package com.cascalc.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeProblemsTest {

    private val engine = CasEngine()
    private val practice = PracticeProblems(Random(42))

    @Test fun `generates a problem for every topic and can answer it`() {
        for (topic in PracticeProblems.Topic.entries) {
            val problem = practice.generate(topic)
            assertTrue("blank question for $topic", problem.question.isNotBlank())
            assertEquals(
                "own answer marked wrong for $topic (${problem.question})",
                PracticeProblems.Mark.Correct,
                practice.mark(problem, problem.answer, engine),
            )
        }
    }

    @Test fun `marks equivalent forms as correct`() {
        val problem = PracticeProblems.Problem(
            PracticeProblems.Topic.FRACTIONS, "1/2", "1/2", "",
        )
        assertEquals(PracticeProblems.Mark.Correct, practice.mark(problem, "0.5", engine))
        assertEquals(PracticeProblems.Mark.Correct, practice.mark(problem, "2/4", engine))
        assertEquals(PracticeProblems.Mark.Correct, practice.mark(problem, "1/2", engine))
    }

    @Test fun `marks a wrong answer as incorrect`() {
        val problem = PracticeProblems.Problem(
            PracticeProblems.Topic.ARITHMETIC, "2 x 3", "6", "",
        )
        assertTrue(practice.mark(problem, "7", engine) is PracticeProblems.Mark.Incorrect)
    }

    @Test fun `marks unreadable input as unreadable, not wrong`() {
        val problem = PracticeProblems.Problem(
            PracticeProblems.Topic.ARITHMETIC, "2 x 3", "6", "",
        )
        assertEquals(PracticeProblems.Mark.Unreadable, practice.mark(problem, "", engine))
        assertEquals(PracticeProblems.Mark.Unreadable, practice.mark(problem, "6 +", engine))
    }

    @Test fun `accepts multiple roots in either order`() {
        val problem = PracticeProblems.Problem(
            PracticeProblems.Topic.QUADRATIC_EQUATIONS, "x^2-1=0", "-1, 1", "",
        )
        assertEquals(PracticeProblems.Mark.Correct, practice.mark(problem, "1, -1", engine))
        assertEquals(PracticeProblems.Mark.Correct, practice.mark(problem, "-1, 1", engine))
        assertTrue(practice.mark(problem, "1, 2", engine) is PracticeProblems.Mark.Incorrect)
    }

    @Test fun `quadratics are generated from known roots so grading is independent of the solver`() {
        repeat(20) {
            val problem = practice.generate(PracticeProblems.Topic.QUADRATIC_EQUATIONS)
            assertEquals(
                PracticeProblems.Mark.Correct,
                practice.mark(problem, problem.answer, engine),
            )
        }
    }
}
