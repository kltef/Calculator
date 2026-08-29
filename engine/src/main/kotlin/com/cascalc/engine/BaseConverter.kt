package com.cascalc.engine

import java.math.BigInteger

/**
 * Conversion between number bases.
 *
 * Uses [BigInteger] rather than `Long` so that the arbitrary-size integers the
 * rest of the calculator produces (`2^100` and friends) survive being displayed
 * in hex or binary.
 */
object BaseConverter {

    const val MIN_BASE = 2
    const val MAX_BASE = 36

    sealed interface Result {
        data class Converted(val text: String) : Result
        data class BadBase(val base: Int) : Result
        data class BadDigits(val text: String, val base: Int) : Result
    }

    fun parse(text: String, base: Int): Result {
        if (base !in MIN_BASE..MAX_BASE) return Result.BadBase(base)
        val cleaned = text.trim().removePrefix("0x").removePrefix("0b").replace("_", "")
        if (cleaned.isEmpty()) return Result.BadDigits(text, base)
        return try {
            Result.Converted(BigInteger(cleaned, base).toString())
        } catch (e: NumberFormatException) {
            Result.BadDigits(text, base)
        }
    }

    fun format(value: BigInteger, base: Int): Result {
        if (base !in MIN_BASE..MAX_BASE) return Result.BadBase(base)
        return Result.Converted(value.toString(base).uppercase())
    }

    /** Convenience: decimal string in, [base] out. */
    fun formatDecimal(decimal: String, base: Int): Result = try {
        format(BigInteger(decimal.trim()), base)
    } catch (e: NumberFormatException) {
        Result.BadDigits(decimal, 10)
    }

    /** The prefixed form people expect for the common bases. */
    fun withPrefix(text: String, base: Int): String = when (base) {
        2 -> "0b$text"
        8 -> "0o$text"
        16 -> "0x$text"
        else -> "${text}₍${base}₎"
    }
}
