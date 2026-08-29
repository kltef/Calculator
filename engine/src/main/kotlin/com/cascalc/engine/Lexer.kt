package com.cascalc.engine

internal enum class TokenType { NUMBER, IDENT, LPAREN, RPAREN, COMMA, OPERATOR, PERCENT }

internal data class Token(val type: TokenType, val text: String)

/**
 * Tokenizes calculator input. This is deliberately not a parser: it only needs
 * enough structure for [InputNormalizer] to rename functions, insert implicit
 * multiplication and locate function arguments. Anything it does not recognise
 * is passed through as an operator token so Symja can report the syntax error.
 */
internal object Lexer {

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++

                c.isDigit() || (c == '.' && i + 1 < input.length && input[i + 1].isDigit()) -> {
                    val start = i
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    tokens += Token(TokenType.NUMBER, input.substring(start, i))
                }

                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '_')) i++
                    tokens += Token(TokenType.IDENT, input.substring(start, i))
                }

                c == '(' || c == '[' -> { tokens += Token(TokenType.LPAREN, "("); i++ }
                c == ')' || c == ']' -> { tokens += Token(TokenType.RPAREN, ")"); i++ }
                c == ',' -> { tokens += Token(TokenType.COMMA, ","); i++ }
                c == '%' -> { tokens += Token(TokenType.PERCENT, "%"); i++ }

                else -> {
                    // Multi-character operators first, then single characters.
                    val two = if (i + 1 < input.length) input.substring(i, i + 2) else ""
                    if (two == "**") {
                        tokens += Token(TokenType.OPERATOR, "^"); i += 2
                    } else {
                        tokens += Token(TokenType.OPERATOR, c.toString()); i++
                    }
                }
            }
        }
        return tokens
    }
}
