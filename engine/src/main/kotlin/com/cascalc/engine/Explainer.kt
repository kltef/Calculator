package com.cascalc.engine

/**
 * Explains an expression the way a person would: what it says, what each
 * symbol does, what order the work happens in, and only then the answer.
 *
 * Every factual claim is computed by the engine rather than asserted, so the
 * explanation cannot describe an expression it has not actually evaluated. The
 * teaching parts — what `+` means, why `×` happens first — come from
 * [Glossary], which is fixed text about fixed symbols.
 */
class Explainer(private val engine: CasEngine) {

    data class Section(val title: String, val lines: List<String>)

    /**
     * @param headline what kind of thing this is
     * @param reading the expression said aloud, e.g. "1 plus 2 times 8"
     * @param sections the body, in reading order
     */
    data class Explanation(
        val headline: String,
        val reading: String?,
        val sections: List<Section>,
    ) {
        /** Flattened text, for sharing or reading out. */
        val plainText: String
            get() = buildString {
                appendLine(headline)
                reading?.let { appendLine(it) }
                sections.forEach { section ->
                    appendLine()
                    appendLine(section.title)
                    section.lines.forEach { appendLine("  $it") }
                }
            }.trim()
    }

    fun explain(input: String, angleMode: AngleMode = AngleMode.RADIANS): Explanation? {
        if (input.isBlank()) return null

        val normalized = try {
            InputNormalizer.normalize(input, angleMode)
        } catch (e: RuntimeException) {
            return null
        }
        val tokens = Lexer.tokenize(normalized)
        val isEquation = CommandParser.parse(input) is Command.Equation

        val sections = buildList {
            symbolsSection(tokens)?.let { add(it) }
            orderSection(tokens)?.let { add(it) }
            if (isEquation) {
                addAll(equationSections(input, angleMode))
            } else {
                addAll(expressionSections(input, angleMode))
            }
        }

        return Explanation(
            headline = headline(input, tokens, isEquation, angleMode),
            reading = readAloud(tokens),
            sections = sections,
        )
    }

    // --- headline and reading --------------------------------------------

    private fun headline(
        input: String,
        tokens: List<Token>,
        isEquation: Boolean,
        angleMode: AngleMode,
    ): String {
        if (isEquation) {
            return when (degreeOf(input, angleMode)) {
                1 -> "Linear equation"
                2 -> "Quadratic equation"
                3 -> "Cubic equation"
                null -> "Equation"
                else -> "Polynomial equation"
            }
        }
        val operators = operatorsIn(tokens)
        return when {
            operators.isEmpty() -> "A number"
            operators == setOf("+") -> "An addition"
            operators == setOf("-") -> "A subtraction"
            operators == setOf("*") -> "A multiplication"
            operators == setOf("/") -> "A division"
            operators.contains("^") -> "A power"
            operators.size > 1 -> "A mixed calculation"
            else -> "An expression"
        }
    }

    /** "1+2*8" -> "1 plus 2 times 8". */
    private fun readAloud(tokens: List<Token>): String? {
        if (tokens.isEmpty()) return null
        val words = tokens.mapNotNull { token ->
            when (token.type) {
                TokenType.NUMBER -> token.text
                TokenType.IDENT -> Glossary.forFunction(token.text)?.name ?: token.text
                TokenType.OPERATOR -> Glossary.spoken(token.text) ?: token.text
                TokenType.LPAREN -> "("
                TokenType.RPAREN -> ")"
                TokenType.PERCENT -> "percent"
                TokenType.COMMA -> ","
            }
        }
        return words.joinToString(" ").takeIf { it.isNotBlank() }
    }

    // --- teaching sections ------------------------------------------------

    /** What each symbol in this expression does. */
    private fun symbolsSection(tokens: List<Token>): Section? {
        val entries = LinkedHashMap<String, Glossary.Entry>()
        for (token in tokens) {
            val entry = when (token.type) {
                TokenType.OPERATOR -> Glossary.forOperator(token.text)
                TokenType.PERCENT -> Glossary.forOperator("%")
                TokenType.IDENT -> Glossary.forFunction(token.text)
                else -> null
            }
            entry?.let { entries.putIfAbsent(it.symbol, it) }
        }
        if (entries.isEmpty()) return null

        return Section(
            "What the symbols mean",
            entries.values.map { "${it.symbol}  (${it.name}) — ${it.meaning}  e.g. ${it.example}" },
        )
    }

    /**
     * Why the answer is what it is when operators of different strength meet.
     *
     * This is the single most common source of "the calculator is wrong":
     * `1 + 2 × 8 × 9` is 145, not 216, because multiplication is done first.
     */
    private fun orderSection(tokens: List<Token>): Section? {
        val present = operatorsIn(tokens).mapNotNull { Glossary.forOperator(it) }
        val ranks = present.map { it.precedence }.distinct()
        if (ranks.size < 2) return null

        val strongest = present.filter { it.precedence == ranks.max() }
        val weakest = present.filter { it.precedence == ranks.min() }
        val strongNames = strongest.joinToString(" and ") { it.symbol }
        val weakNames = weakest.joinToString(" and ") { it.symbol }

        return Section(
            "Which part happens first",
            listOf(
                "$strongNames is worked out before $weakNames.",
                "Brackets override that — anything inside them is done first.",
            ),
        )
    }

    // --- computed facts ---------------------------------------------------

    private fun expressionSections(input: String, angleMode: AngleMode): List<Section> {
        val result = engine.evaluate(input, angleMode)
        if (result !is CalcResult.Success) {
            return listOf(Section("The answer", listOf("This can't be worked out as written.")))
        }

        val answer = buildList {
            add("= ${result.exact}")
            result.approximate?.let { add("about $it as a decimal") }
        }

        val about = buildList {
            val whole = result.raw.toLongOrNull()
            if (whole != null && whole > 1) {
                primeFact(whole)?.let { add(it) }
            }
            if (result.exact.contains("/")) {
                add("This is an exact fraction — nothing has been rounded away.")
            }
            val factored = engine.evaluate(input, angleMode, Action.FACTOR)
            if (factored is CalcResult.Success && factored.exact != result.exact) {
                add("Factors as ${factored.exact}")
            }
        }

        return buildList {
            add(Section("The answer", answer))
            if (about.isNotEmpty()) add(Section("Worth noticing", about))
        }
    }

    private fun equationSections(input: String, angleMode: AngleMode): List<Section> {
        val solved = engine.evaluate(input, angleMode, Action.SOLVE)
        val answer = when (solved) {
            is CalcResult.Success -> when {
                solved.exact.startsWith("Any value") ->
                    listOf("True whatever the unknown is — an identity, not a puzzle.")
                else -> {
                    val count = solved.exact.split(",").size
                    listOf(
                        solved.exact,
                        if (count == 1) "One value makes both sides match."
                        else "$count values make both sides match.",
                    )
                }
            }
            is CalcResult.Failure -> listOf(solved.message)
            CalcResult.Empty -> emptyList()
        }

        val working = (solved as? CalcResult.Success)?.steps
            ?.map { step -> step.expression?.let { "${step.explanation}  →  $it" } ?: step.explanation }
            .orEmpty()

        return buildList {
            add(Section("The answer", answer))
            if (working.isNotEmpty()) add(Section("How it is solved", working))
        }
    }

    private fun degreeOf(input: String, angleMode: AngleMode): Int? {
        val command = CommandParser.parse(input) as? Command.Equation ?: return null
        val moved = "(${command.leftText}) - (${command.rightText})"
        val variable = engine.plotVariable(moved, angleMode) ?: return null
        val result = engine.evaluate("Exponent(Expand($moved), $variable)", angleMode)
        return (result as? CalcResult.Success)?.raw?.toIntOrNull()
    }

    private fun primeFact(value: Long): String? {
        val prime = engine.evaluate("PrimeQ($value)")
        if (prime is CalcResult.Success && prime.raw == "True") {
            return "$value is prime — it divides by nothing but 1 and itself."
        }
        val factors = engine.evaluate("FactorInteger($value)")
        val raw = (factors as? CalcResult.Success)?.raw ?: return null
        return formatFactorisation(raw)?.let { "Breaks into primes as $it" }
    }

    private fun operatorsIn(tokens: List<Token>): Set<String> =
        tokens.mapNotNull {
            when (it.type) {
                TokenType.OPERATOR -> it.text.takeIf { text -> Glossary.forOperator(text) != null }
                TokenType.PERCENT -> "%"
                else -> null
            }
        }.toSet()

    /**
     * Turns Symja's `{{2,1},{71,1},{809,1}}` into `2 × 71 × 809`.
     *
     * The pair-of-pairs form is how the library represents base and exponent,
     * and it is unreadable on a card floating over a page.
     */
    fun formatFactorisation(raw: String): String? {
        if (!raw.startsWith("{{") || !raw.endsWith("}}")) return null
        val pairs = splitTopLevel(raw.substring(1, raw.length - 1))

        val parts = pairs.map { pair ->
            if (!pair.startsWith("{") || !pair.endsWith("}")) return null
            val fields = splitTopLevel(pair.substring(1, pair.length - 1))
            if (fields.size != 2) return null
            val base = fields[0].trim()
            val exponent = fields[1].trim()
            if (exponent == "1") base else "$base${superscript(exponent)}"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" × ")
    }

    private fun superscript(number: String): String =
        number.map { SUPERSCRIPTS[it] ?: it }.joinToString("")

    private fun splitTopLevel(text: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        text.forEachIndexed { index, c ->
            when (c) {
                '{' -> depth++
                '}' -> depth--
                ',' -> if (depth == 0) {
                    parts += text.substring(start, index)
                    start = index + 1
                }
            }
        }
        parts += text.substring(start)
        return parts.map { it.trim() }
    }

    private companion object {
        val SUPERSCRIPTS = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        )
    }
}
