package com.cascalc.engine

/**
 * Translates the notation people actually type on a calculator keypad into the
 * Wolfram-style syntax Symja expects.
 *
 * Responsibilities:
 *  - unicode operators (x, /, -, sqrt, pi) -> ASCII / Symja equivalents
 *  - lowercase function names -> Symja's capitalised names (`sin` -> `Sin`)
 *  - implicit multiplication (`2(3+4)`, `2x`, `(1+2)(3+4)`)
 *  - postfix percent (`20%` -> `20/100`)
 *  - degree mode, by rewriting trig arguments and inverse-trig results
 */
object InputNormalizer {

    /** Functions whose single argument is an angle. */
    private val FORWARD_TRIG = setOf("Sin", "Cos", "Tan", "Csc", "Sec", "Cot")

    /** Functions whose result is an angle. */
    private val INVERSE_TRIG = setOf("ArcSin", "ArcCos", "ArcTan", "ArcCsc", "ArcSec", "ArcCot")

    /** User-facing name -> Symja name. Keys are compared lowercase. */
    private val FUNCTIONS: Map<String, String> = buildMap {
        listOf(
            "sin" to "Sin", "cos" to "Cos", "tan" to "Tan",
            "csc" to "Csc", "sec" to "Sec", "cot" to "Cot",
            "asin" to "ArcSin", "arcsin" to "ArcSin",
            "acos" to "ArcCos", "arccos" to "ArcCos",
            "atan" to "ArcTan", "arctan" to "ArcTan",
            "sinh" to "Sinh", "cosh" to "Cosh", "tanh" to "Tanh",
            "sqrt" to "Sqrt", "cbrt" to "Surd", "abs" to "Abs",
            "ln" to "Log", "log" to "Log10", "log10" to "Log10", "log2" to "Log2",
            "exp" to "Exp", "floor" to "Floor", "ceil" to "Ceiling", "ceiling" to "Ceiling",
            "round" to "Round", "sign" to "Sign", "gcd" to "GCD", "lcm" to "LCM",
            "max" to "Max", "min" to "Min", "mod" to "Mod", "factorial" to "Factorial",
            "nthroot" to "Surd",

            // V4 - calculus
            "d" to "D", "diff" to "D", "derivative" to "D",
            "integrate" to "Integrate", "integral" to "Integrate",
            "limit" to "Limit", "sum" to "Sum", "product" to "Product",
            "series" to "Series", "taylor" to "Series",

            // V5 - linear algebra
            "det" to "Det", "determinant" to "Det", "inverse" to "Inverse",
            "transpose" to "Transpose", "eigenvalues" to "Eigenvalues",
            "eigenvectors" to "Eigenvectors", "rowreduce" to "RowReduce",
            "rref" to "RowReduce", "rank" to "MatrixRank", "matrixrank" to "MatrixRank",
            "linearsolve" to "LinearSolve", "identity" to "IdentityMatrix",
            "dot" to "Dot", "cross" to "Cross", "norm" to "Norm", "trace" to "Tr",

            // V6 - number theory
            "isprime" to "PrimeQ", "primeq" to "PrimeQ", "prime" to "Prime",
            "nextprime" to "NextPrime", "factorint" to "FactorInteger",
            "factorinteger" to "FactorInteger", "divisors" to "Divisors",
            "eulerphi" to "EulerPhi", "totient" to "EulerPhi", "binomial" to "Binomial",
            "fibonacci" to "Fibonacci", "powermod" to "PowerMod",

            // V6 - statistics
            "mean" to "Mean", "median" to "Median", "mode" to "Commonest",
            "stdev" to "StandardDeviation", "sd" to "StandardDeviation",
            "standarddeviation" to "StandardDeviation", "variance" to "Variance",
            "var" to "Variance", "total" to "Total", "sort" to "Sort",
            "quantile" to "Quantile", "correlation" to "Correlation",
        ).forEach { (k, v) -> put(k, v) }
    }

    /** Bare symbols the user may type. */
    private val CONSTANTS = mapOf(
        "pi" to "Pi",
        "tau" to "(2*Pi)",
        "e" to "E",
        "inf" to "Infinity",
        "infinity" to "Infinity",
    )

    private val UNICODE_REPLACEMENTS = listOf(
        "×" to "*",   // ×
        "⋅" to "*",   // ⋅
        "·" to "*",   // ·
        "÷" to "/",   // ÷
        "−" to "-",   // −
        "–" to "-",   // –
        "—" to "-",   // —
        "π" to " pi ",  // π
        "√" to " sqrt ", // √
        "⁻¹" to "^(-1)", // ⁻¹
        "²" to "^2",  // ²
        "³" to "^3",  // ³
    )

    fun normalize(input: String, angleMode: AngleMode = AngleMode.RADIANS): String {
        var text = input
        for ((from, to) in UNICODE_REPLACEMENTS) text = text.replace(from, to)

        var tokens = Lexer.tokenize(text).map(::mapIdentifier)
        tokens = expandBareSqrt(tokens)
        tokens = expandPercent(tokens)
        tokens = insertImplicitMultiplication(tokens)
        if (angleMode == AngleMode.DEGREES) tokens = applyDegreeMode(tokens)

        return tokens.joinToString("") { it.text }
    }

    private fun mapIdentifier(token: Token): Token {
        if (token.type != TokenType.IDENT) return token
        val lower = token.text.lowercase()
        FUNCTIONS[lower]?.let { return token.copy(text = it) }
        CONSTANTS[lower]?.let { return token.copy(text = it) }
        return token
    }

    /** `sqrt 9` / `sqrt9` (no parentheses) -> `Sqrt(9)`. */
    private fun expandBareSqrt(tokens: List<Token>): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            if (t.type == TokenType.IDENT && t.text == "Sqrt" &&
                next != null && (next.type == TokenType.NUMBER || next.type == TokenType.IDENT)
            ) {
                out += t
                out += Token(TokenType.LPAREN, "(")
                out += next
                out += Token(TokenType.RPAREN, ")")
                i += 2
            } else {
                out += t
                i++
            }
        }
        return out
    }

    /** Postfix percent: `20%` -> `(20/100)`. */
    private fun expandPercent(tokens: List<Token>): List<Token> {
        val out = mutableListOf<Token>()
        for (t in tokens) {
            if (t.type == TokenType.PERCENT) {
                out += Token(TokenType.OPERATOR, "/")
                out += Token(TokenType.NUMBER, "100")
            } else {
                out += t
            }
        }
        return out
    }

    private fun insertImplicitMultiplication(tokens: List<Token>): List<Token> {
        val out = mutableListOf<Token>()
        for ((index, token) in tokens.withIndex()) {
            val prev = tokens.getOrNull(index - 1)
            if (prev != null && needsMultiplication(prev, token)) {
                out += Token(TokenType.OPERATOR, "*")
            }
            out += token
        }
        return out
    }

    private fun needsMultiplication(prev: Token, current: Token): Boolean {
        val prevEndsValue = when (prev.type) {
            TokenType.NUMBER, TokenType.RPAREN -> true
            // An identifier only ends a value if it is not a function being called.
            TokenType.IDENT -> current.type != TokenType.LPAREN
            else -> false
        }
        if (!prevEndsValue) return false

        return when (current.type) {
            TokenType.NUMBER -> prev.type != TokenType.NUMBER
            TokenType.LPAREN -> true
            // `2x`, `2Sqrt(2)`, `(1+2)x` -- but never `Sqrt(` handled above.
            TokenType.IDENT -> true
            else -> false
        }
    }

    /**
     * Degree mode. Forward trig gets its argument scaled by `Degree`; inverse
     * trig gets its (radian) result scaled back. Wrapping the whole argument in
     * parentheses keeps `sin(1+2)` correct rather than scaling only the last term.
     */
    private fun applyDegreeMode(tokens: List<Token>): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val isForward = t.type == TokenType.IDENT && t.text in FORWARD_TRIG
            val isInverse = t.type == TokenType.IDENT && t.text in INVERSE_TRIG
            val close = if ((isForward || isInverse) && tokens.getOrNull(i + 1)?.type == TokenType.LPAREN) {
                matchingParen(tokens, i + 1)
            } else {
                -1
            }
            if (close == -1) {
                out += t
                i++
                continue
            }
            // Nested calls are converted too: sin(cos(30)) scales both angles.
            val inner = applyDegreeMode(tokens.subList(i + 2, close))
            if (isForward) {
                out += t
                out += LPAREN
                out += LPAREN
                out += inner
                out += RPAREN
                out += TIMES
                out += DEGREE
                out += RPAREN
            } else {
                out += LPAREN
                out += t
                out += LPAREN
                out += inner
                out += RPAREN
                out += RPAREN
                out += DIVIDE
                out += DEGREE
            }
            i = close + 1
        }
        return out
    }

    private val LPAREN = Token(TokenType.LPAREN, "(")
    private val RPAREN = Token(TokenType.RPAREN, ")")
    private val TIMES = Token(TokenType.OPERATOR, "*")
    private val DIVIDE = Token(TokenType.OPERATOR, "/")
    private val DEGREE = Token(TokenType.IDENT, "Degree")

    /** Index of the `)` matching the `(` at [openIndex], or -1 if unbalanced. */
    private fun matchingParen(tokens: List<Token>, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until tokens.size) {
            when (tokens[i].type) {
                TokenType.LPAREN -> depth++
                TokenType.RPAREN -> {
                    depth--
                    if (depth == 0) return i
                }
                else -> {}
            }
        }
        return -1
    }
}
