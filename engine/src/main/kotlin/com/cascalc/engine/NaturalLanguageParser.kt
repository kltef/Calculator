package com.cascalc.engine

/**
 * Turns spoken or typed English into a calculator expression.
 *
 * **The V6 decision, made deliberately: this is a local rule-based parser, not
 * an LLM.** A network round trip for "twenty percent of 150" is the wrong
 * trade — it fails on a train, costs money per tap, adds latency to something
 * that must feel instant, and sends the user's working to a third party. The
 * price is that it understands a fixed vocabulary and nothing outside it, so
 * it reports honestly when it does not understand rather than guessing.
 *
 * Anything it cannot parse is returned as [Result.NotUnderstood], and the
 * caller falls back to treating the input as an ordinary expression.
 */
object NaturalLanguageParser {

    sealed interface Result {
        /** @param expression calculator syntax, ready for [InputNormalizer] */
        data class Parsed(val expression: String, val interpretation: String) : Result
        data object NotUnderstood : Result
    }

    private val NUMBER_WORDS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90, "hundred" to 100, "thousand" to 1000,
        "million" to 1000000,
    )

    /** Phrases rewritten to operators, longest first so "divided by" beats "by". */
    private val PHRASES: List<Pair<String, String>> = listOf(
        "to the power of" to "^",
        "raised to the power" to "^",
        "divided by" to "/",
        "multiplied by" to "*",
        "square root of" to "sqrt",
        "cube root of" to "cbrt",
        "the square of" to "sq:",
        "squared" to "^2",
        "cubed" to "^3",
        "percent of" to "% *",
        "per cent of" to "% *",
        "percent" to "%",
        "per cent" to "%",
        "plus" to "+",
        "minus" to "-",
        "times" to "*",
        "over" to "/",
        "add" to "+",
        "subtract" to "-",
        "less" to "-",
        "and" to "+",
    )

    /** Words carrying no arithmetic meaning. */
    private val FILLER = setOf(
        "what", "whats", "what's", "is", "the", "of", "please", "calculate",
        "compute", "work", "out", "tell", "me", "much", "how", "equals", "equal",
        "to", "a", "result", "answer", "value",
    )

    fun parse(input: String): Result {
        if (input.isBlank()) return Result.NotUnderstood

        var text = input.lowercase().trim().trimEnd('?', '.', '!')
        // Digits stay as they are; words become digits before phrases are applied
        // so "twenty percent of 150" and "20% of 150" take the same path.
        text = replaceNumberWords(text)
        for ((phrase, replacement) in PHRASES) {
            text = text.replace(Regex("\\b${Regex.escape(phrase)}\\b"), " $replacement ")
        }
        text = applySquareOf(text)
        text = dropFiller(text)
        text = text.replace(Regex("\\s+"), " ").trim()

        if (text.isEmpty()) return Result.NotUnderstood
        if (!text.any { it.isDigit() || it in "xyzt" }) return Result.NotUnderstood
        // Anything left that is not part of the calculator's vocabulary means we
        // guessed rather than understood.
        if (text.any { it.isLetter() && !isKnownLetterRun(text) }) return Result.NotUnderstood

        val expression = text.replace(Regex("\\s+"), " ").trim()
        return Result.Parsed(expression, "Read as: $expression")
    }

    /** `the square of 5` -> `(5)^2`, applied after phrase substitution. */
    private fun applySquareOf(text: String): String =
        Regex("sq:\\s*([0-9.]+)").replace(text) { "(${it.groupValues[1]})^2" }

    private fun dropFiller(text: String): String =
        text.split(" ").filter { it.isNotBlank() && it !in FILLER }.joinToString(" ")

    /** Only calculator function names may remain as letters. */
    private fun isKnownLetterRun(text: String): Boolean {
        val words = Regex("[a-z]+").findAll(text).map { it.value }.toList()
        val allowed = setOf(
            "sqrt", "cbrt", "sin", "cos", "tan", "log", "ln", "abs", "exp",
            "pi", "e", "x", "y", "z", "t", "n",
        )
        return words.all { it in allowed }
    }

    /**
     * Converts spelled-out numbers, including compounds like "twenty five" and
     * "two hundred and fifty" (the "and" is consumed here so it is not later
     * mistaken for addition).
     */
    private fun replaceNumberWords(text: String): String {
        val tokens = text.split(" ").filter { it.isNotBlank() }
        val output = mutableListOf<String>()
        var index = 0

        while (index < tokens.size) {
            if (tokens[index].trim('-') !in NUMBER_WORDS) {
                output += tokens[index]
                index++
                continue
            }
            var total = 0
            var running = 0
            var consumed = false
            while (index < tokens.size) {
                val word = tokens[index].trim('-')
                val value = NUMBER_WORDS[word]
                if (value == null) {
                    // "and" inside a number ("two hundred and five") continues it.
                    val isJoiner = word == "and" &&
                        index + 1 < tokens.size && tokens[index + 1].trim('-') in NUMBER_WORDS
                    if (!isJoiner) break
                    index++
                    continue
                }
                when {
                    value == 100 -> running = (if (running == 0) 1 else running) * 100
                    value >= 1000 -> {
                        total += (if (running == 0) 1 else running) * value
                        running = 0
                    }
                    else -> running += value
                }
                consumed = true
                index++
            }
            if (consumed) output += (total + running).toString()
        }
        return output.joinToString(" ")
    }
}
