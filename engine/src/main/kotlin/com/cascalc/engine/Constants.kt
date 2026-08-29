package com.cascalc.engine

/**
 * Physical and mathematical constants, insertable into an expression.
 *
 * Values follow the 2019 SI redefinition, where several of these are exact by
 * definition rather than measured — noted per entry, because "exact" is a real
 * property of the constant and not a claim about this table's precision.
 */
object Constants {

    data class Constant(
        val symbol: String,
        val name: String,
        /** Symja expression for the value, kept exact where the constant is. */
        val expression: String,
        val unit: String,
        val exact: Boolean = false,
    )

    val ALL: List<Constant> = listOf(
        Constant("π", "Pi", "Pi", "", exact = true),
        Constant("e", "Euler's number", "E", "", exact = true),
        Constant("φ", "Golden ratio", "(1 + Sqrt(5))/2", "", exact = true),
        Constant("c", "Speed of light in vacuum", "299792458", "m/s", exact = true),
        Constant("h", "Planck constant", "6.62607015*10^-34", "J·s", exact = true),
        Constant("ℏ", "Reduced Planck constant", "6.62607015*10^-34/(2*Pi)", "J·s", exact = true),
        Constant("e₀", "Elementary charge", "1.602176634*10^-19", "C", exact = true),
        Constant("k", "Boltzmann constant", "1.380649*10^-23", "J/K", exact = true),
        Constant("N_A", "Avogadro constant", "6.02214076*10^23", "1/mol", exact = true),
        Constant("R", "Molar gas constant", "8.31446261815324", "J/(mol·K)", exact = true),
        Constant("G", "Gravitational constant", "6.67430*10^-11", "m³/(kg·s²)"),
        Constant("g", "Standard gravity", "9.80665", "m/s²", exact = true),
        Constant("m_e", "Electron mass", "9.1093837015*10^-31", "kg"),
        Constant("m_p", "Proton mass", "1.67262192369*10^-27", "kg"),
        Constant("σ", "Stefan-Boltzmann constant", "5.670374419*10^-8", "W/(m²·K⁴)"),
        Constant("ε₀", "Vacuum permittivity", "8.8541878128*10^-12", "F/m"),
        Constant("μ₀", "Vacuum permeability", "1.25663706212*10^-6", "N/A²"),
        Constant("atm", "Standard atmosphere", "101325", "Pa", exact = true),
    )

    fun find(symbolOrName: String): Constant? {
        val needle = symbolOrName.trim().lowercase()
        return ALL.firstOrNull {
            it.symbol.lowercase() == needle || it.name.lowercase() == needle
        }
    }
}
