package com.cascalc.engine

/**
 * Unit conversion.
 *
 * Symja's `UnitConvert` is not available in the build the app uses, so this is
 * a self-contained table. Each unit records how to reach its dimension's base
 * unit as `value * factor + offset`; the offset exists because temperature is
 * affine rather than proportional — 0 °C is not 0 °F, and treating it as a pure
 * ratio is the classic way to get temperature conversion wrong.
 */
object Units {

    data class Unit(
        val name: String,
        val dimension: String,
        val factor: Double,
        val offset: Double = 0.0,
        val aliases: List<String> = emptyList(),
    ) {
        fun toBase(value: Double): Double = value * factor + offset
        fun fromBase(base: Double): Double = (base - offset) / factor
    }

    data class Conversion(val value: Double, val from: Unit, val to: Unit)

    private val UNITS: List<Unit> = listOf(
        // length (base: metre)
        Unit("metre", "length", 1.0, aliases = listOf("m", "meter", "metres", "meters")),
        Unit("kilometre", "length", 1000.0, aliases = listOf("km", "kilometer", "kilometres", "kilometers")),
        Unit("centimetre", "length", 0.01, aliases = listOf("cm", "centimeter", "centimetres", "centimeters")),
        Unit("millimetre", "length", 0.001, aliases = listOf("mm", "millimeter", "millimetres", "millimeters")),
        Unit("mile", "length", 1609.344, aliases = listOf("mi", "miles")),
        Unit("yard", "length", 0.9144, aliases = listOf("yd", "yards")),
        Unit("foot", "length", 0.3048, aliases = listOf("ft", "feet")),
        Unit("inch", "length", 0.0254, aliases = listOf("in", "inches")),
        Unit("nautical mile", "length", 1852.0, aliases = listOf("nmi")),

        // mass (base: kilogram)
        Unit("kilogram", "mass", 1.0, aliases = listOf("kg", "kilograms")),
        Unit("gram", "mass", 0.001, aliases = listOf("g", "grams")),
        Unit("milligram", "mass", 1e-6, aliases = listOf("mg", "milligrams")),
        Unit("tonne", "mass", 1000.0, aliases = listOf("t", "tonnes", "metric ton")),
        Unit("pound", "mass", 0.45359237, aliases = listOf("lb", "lbs", "pounds")),
        Unit("ounce", "mass", 0.028349523125, aliases = listOf("oz", "ounces")),
        Unit("stone", "mass", 6.35029318, aliases = listOf("st", "stones")),

        // time (base: second)
        Unit("second", "time", 1.0, aliases = listOf("s", "sec", "secs", "seconds")),
        Unit("minute", "time", 60.0, aliases = listOf("min", "mins", "minutes")),
        Unit("hour", "time", 3600.0, aliases = listOf("h", "hr", "hrs", "hours")),
        Unit("day", "time", 86400.0, aliases = listOf("d", "days")),
        Unit("week", "time", 604800.0, aliases = listOf("wk", "weeks")),
        Unit("year", "time", 31557600.0, aliases = listOf("yr", "years")),

        // temperature (base: kelvin) -- affine
        Unit("kelvin", "temperature", 1.0, aliases = listOf("k")),
        Unit("celsius", "temperature", 1.0, offset = 273.15, aliases = listOf("c", "degc", "centigrade")),
        Unit("fahrenheit", "temperature", 5.0 / 9.0, offset = 273.15 - 32.0 * 5.0 / 9.0, aliases = listOf("f", "degf")),

        // volume (base: litre)
        Unit("litre", "volume", 1.0, aliases = listOf("l", "liter", "litres", "liters")),
        Unit("millilitre", "volume", 0.001, aliases = listOf("ml", "milliliter", "millilitres", "milliliters")),
        Unit("gallon", "volume", 3.785411784, aliases = listOf("gal", "gallons")),
        Unit("pint", "volume", 0.473176473, aliases = listOf("pt", "pints")),
        Unit("cup", "volume", 0.2365882365, aliases = listOf("cups")),

        // speed (base: metre per second)
        Unit("metre per second", "speed", 1.0, aliases = listOf("m/s", "mps")),
        Unit("kilometre per hour", "speed", 1 / 3.6, aliases = listOf("km/h", "kph", "kmh")),
        Unit("mile per hour", "speed", 0.44704, aliases = listOf("mph", "mi/h")),
        Unit("knot", "speed", 0.514444, aliases = listOf("kn", "knots")),

        // data (base: byte)
        Unit("byte", "data", 1.0, aliases = listOf("b", "bytes")),
        Unit("kilobyte", "data", 1024.0, aliases = listOf("kb", "kilobytes")),
        Unit("megabyte", "data", 1024.0 * 1024, aliases = listOf("mb", "megabytes")),
        Unit("gigabyte", "data", 1024.0 * 1024 * 1024, aliases = listOf("gb", "gigabytes")),
        Unit("terabyte", "data", 1024.0 * 1024 * 1024 * 1024, aliases = listOf("tb", "terabytes")),

        // angle (base: radian)
        Unit("radian", "angle", 1.0, aliases = listOf("rad", "radians")),
        Unit("degree", "angle", Math.PI / 180, aliases = listOf("deg", "degrees")),
    )

    private val BY_NAME: Map<String, Unit> = buildMap {
        for (unit in UNITS) {
            put(unit.name.lowercase(), unit)
            for (alias in unit.aliases) put(alias.lowercase(), unit)
        }
    }

    fun find(name: String): Unit? = BY_NAME[name.trim().lowercase()]

    /** All units of the same dimension as [unit], for offering alternatives. */
    fun peersOf(unit: Unit): List<Unit> = UNITS.filter { it.dimension == unit.dimension }

    val dimensions: List<String> get() = UNITS.map { it.dimension }.distinct()

    fun unitsIn(dimension: String): List<Unit> = UNITS.filter { it.dimension == dimension }

    sealed interface Result {
        data class Converted(val value: Double, val text: String) : Result
        data class Unknown(val name: String) : Result
        data class Mismatched(val from: Unit, val to: Unit) : Result
    }

    fun convert(value: Double, fromName: String, toName: String): Result {
        val from = find(fromName) ?: return Result.Unknown(fromName)
        val to = find(toName) ?: return Result.Unknown(toName)
        if (from.dimension != to.dimension) return Result.Mismatched(from, to)

        val converted = to.fromBase(from.toBase(value))
        return Result.Converted(
            converted,
            "${ResultFormatter.formatDouble(converted)} ${to.name}",
        )
    }
}
