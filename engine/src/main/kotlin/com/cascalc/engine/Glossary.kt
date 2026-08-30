package com.cascalc.engine

/**
 * Plain-language descriptions of the symbols and functions a calculator uses.
 *
 * Wording aims at someone meeting the symbol rather than someone revising it:
 * each entry says what the operation *does*, and gives a concrete instance
 * rather than a definition in terms the reader may not have either.
 */
object Glossary {

    /**
     * @param symbol as written
     * @param name what it is called
     * @param meaning what it does, in one sentence
     * @param example a worked instance, small enough to check mentally
     * @param precedence higher binds tighter; used to explain working order
     */
    data class Entry(
        val symbol: String,
        val name: String,
        val meaning: String,
        val example: String,
        val precedence: Int,
    )

    private val OPERATORS: Map<String, Entry> = listOf(
        Entry("+", "plus", "Adds the two amounts together to make a total.", "3 + 4 = 7", 1),
        Entry("-", "minus", "Takes the second amount away from the first.", "7 − 4 = 3", 1),
        Entry(
            "*", "times",
            "Adds a number to itself repeatedly — 3 × 4 means four lots of 3.",
            "3 × 4 = 12", 2,
        ),
        Entry(
            "/", "divided by",
            "Splits an amount into equal parts, and says how big one part is.",
            "12 ÷ 4 = 3", 2,
        ),
        Entry(
            "^", "to the power of",
            "Multiplies the base by itself, as many times as the power says.",
            "2³ = 2 × 2 × 2 = 8", 3,
        ),
        Entry(
            "=", "equals",
            "Says the two sides are the same value. In an equation you find the " +
                "value that makes that true.",
            "x + 1 = 3 means x is 2", 0,
        ),
        Entry(
            "%", "percent",
            "Means \"out of a hundred\", so 20% is the same as 20/100.",
            "20% of 150 = 30", 2,
        ),
        Entry(
            "!", "factorial",
            "Multiplies every whole number from this one down to 1.",
            "4! = 4 × 3 × 2 × 1 = 24", 4,
        ),
    ).associateBy { it.symbol }

    private val FUNCTIONS: Map<String, Entry> = listOf(
        Entry(
            "Sqrt", "square root",
            "The number that gives this one when multiplied by itself.",
            "√9 = 3, because 3 × 3 = 9", 4,
        ),
        Entry("Sin", "sine", "For an angle, the height of a point on the unit circle.", "sin(30°) = 1/2", 4),
        Entry("Cos", "cosine", "For an angle, the sideways distance on the unit circle.", "cos(60°) = 1/2", 4),
        Entry("Tan", "tangent", "The sine divided by the cosine — the slope of the angle.", "tan(45°) = 1", 4),
        Entry("Log", "natural logarithm", "Asks what power of e gives this number.", "ln(e) = 1", 4),
        Entry("Log10", "logarithm", "Asks what power of 10 gives this number.", "log(1000) = 3", 4),
        Entry("Abs", "absolute value", "The size of a number, ignoring its sign.", "|−5| = 5", 4),
        Entry("Exp", "exponential", "e raised to this power.", "exp(0) = 1", 4),
        Entry("Factorial", "factorial", "Multiplies every whole number down to 1.", "4! = 24", 4),
        Entry("D", "derivative", "The rate at which something changes.", "d/dx x² = 2x", 4),
        Entry("Integrate", "integral", "The running total of a quantity — the area under its curve.", "∫ 2x dx = x²", 4),
        Entry("Limit", "limit", "The value something approaches, without necessarily reaching it.", "sin(x)/x → 1 as x → 0", 4),
        Entry("Det", "determinant", "A single number saying how a matrix scales area or volume.", "det [[1,2],[3,4]] = −2", 4),
    ).associateBy { it.symbol }

    fun forOperator(symbol: String): Entry? = OPERATORS[symbol]

    fun forFunction(name: String): Entry? = FUNCTIONS[name]

    /** Reading of an operator for saying the expression aloud. */
    fun spoken(symbol: String): String? = OPERATORS[symbol]?.name
}
