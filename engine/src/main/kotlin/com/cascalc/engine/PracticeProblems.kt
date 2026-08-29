package com.cascalc.engine

import kotlin.random.Random

/**
 * V7's practice generator: makes problems and marks answers.
 *
 * Problems are generated with a known answer rather than generated and then
 * solved, so grading never depends on the solver getting it right. Marking
 * compares *mathematically*, not textually — `1/2`, `0.5` and `2/4` are the
 * same answer, and a student who writes any of them is correct.
 */
class PracticeProblems(private val random: Random = Random.Default) {

    enum class Topic(val label: String) {
        ARITHMETIC("Arithmetic"),
        FRACTIONS("Fractions"),
        LINEAR_EQUATIONS("Linear equations"),
        QUADRATIC_EQUATIONS("Quadratics"),
        DERIVATIVES("Derivatives"),
        EXPANSION("Expanding brackets"),
    }

    data class Problem(
        val topic: Topic,
        val question: String,
        /** Expression the answer must equal, in calculator syntax. */
        val answer: String,
        val prompt: String,
    )

    fun generate(topic: Topic): Problem = when (topic) {
        Topic.ARITHMETIC -> {
            val a = random.nextInt(12, 100)
            val b = random.nextInt(12, 100)
            Problem(topic, "$a × $b", "${a * b}", "Work it out")
        }

        Topic.FRACTIONS -> {
            val a = random.nextInt(1, 9)
            val b = random.nextInt(2, 10)
            val c = random.nextInt(1, 9)
            val d = random.nextInt(2, 10)
            Problem(topic, "$a/$b + $c/$d", "$a/$b + $c/$d", "Give the answer as a fraction")
        }

        Topic.LINEAR_EQUATIONS -> {
            val a = random.nextInt(2, 9)
            val x = random.nextInt(-9, 10)
            val b = random.nextInt(-20, 20)
            Problem(topic, "${a}x + $b = ${a * x + b}", "$x", "Solve for x")
        }

        Topic.QUADRATIC_EQUATIONS -> {
            // Built from its roots so the answer is known exactly.
            val r1 = random.nextInt(-6, 7)
            val r2 = random.nextInt(-6, 7)
            val b = -(r1 + r2)
            val c = r1 * r2
            Problem(
                topic,
                "x^2 ${signed(b)}x ${signed(c)} = 0",
                if (r1 == r2) "$r1" else "${minOf(r1, r2)}, ${maxOf(r1, r2)}",
                "Solve for x (list both roots if there are two)",
            )
        }

        Topic.DERIVATIVES -> {
            val a = random.nextInt(2, 9)
            val n = random.nextInt(2, 6)
            Problem(topic, "d/dx (${a}x^$n)", "${a * n}x^${n - 1}", "Differentiate")
        }

        Topic.EXPANSION -> {
            val a = random.nextInt(1, 7)
            val b = random.nextInt(1, 7)
            Problem(topic, "(x + $a)(x + $b)", "x^2 + ${a + b}x + ${a * b}", "Expand")
        }
    }

    private fun signed(value: Int): String = if (value < 0) "- ${-value}" else "+ $value"

    sealed interface Mark {
        data object Correct : Mark
        data class Incorrect(val expected: String) : Mark
        data object Unreadable : Mark
    }

    /**
     * Marks [given] against the problem, comparing values rather than text.
     *
     * `PossibleZeroQ(given - expected)` is the test: it answers "are these the
     * same number", so `0.5`, `1/2` and `2/4` all mark correct.
     */
    fun mark(problem: Problem, given: String, engine: CasEngine): Mark {
        if (given.isBlank()) return Mark.Unreadable

        // Multi-root answers are compared as sets, order-insensitively.
        val expectedParts = problem.answer.split(",").map { it.trim() }
        val givenParts = given.split(",").map { it.trim() }
        if (expectedParts.size != givenParts.size) {
            return Mark.Incorrect(problem.answer)
        }

        val remaining = expectedParts.toMutableList()
        for (part in givenParts) {
            val match = remaining.firstOrNull { equal(part, it, engine) }
                ?: return if (parses(part, engine)) {
                    Mark.Incorrect(problem.answer)
                } else {
                    Mark.Unreadable
                }
            remaining -= match
        }
        return if (remaining.isEmpty()) Mark.Correct else Mark.Incorrect(problem.answer)
    }

    private fun equal(a: String, b: String, engine: CasEngine): Boolean {
        val result = engine.evaluate("PossibleZeroQ(($a) - ($b))")
        return result is CalcResult.Success && result.raw == "True"
    }

    private fun parses(text: String, engine: CasEngine): Boolean =
        engine.evaluate(text) is CalcResult.Success
}
