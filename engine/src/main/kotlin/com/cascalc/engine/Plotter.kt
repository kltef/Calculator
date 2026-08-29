package com.cascalc.engine

/** A point in graph (not screen) coordinates. */
data class PlotPoint(val x: Double, val y: Double)

/**
 * One function's sampled curve.
 *
 * [segments] holds runs of connected points. A function is split into several
 * segments wherever it is undefined or jumps — without that, `1/x` draws a
 * spurious vertical line joining -∞ to +∞ across the asymptote.
 */
data class PlotCurve(
    val expression: String,
    val segments: List<List<PlotPoint>>,
) {
    val isEmpty: Boolean get() = segments.all { it.size < 2 }
}

/** The visible window, in graph coordinates. */
data class PlotWindow(
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double,
) {
    val width: Double get() = xMax - xMin
    val height: Double get() = yMax - yMin

    fun isValid(): Boolean =
        xMin.isFinite() && xMax.isFinite() && yMin.isFinite() && yMax.isFinite() &&
            width > 0 && height > 0

    companion object {
        val DEFAULT = PlotWindow(-10.0, 10.0, -10.0, 10.0)
    }
}

/**
 * Samples expressions for plotting, and finds the points a user wants to tap.
 *
 * Everything here is numeric: the exact engine answers "what is this", plotting
 * answers "roughly what shape is this", and forcing exact evaluation per pixel
 * would be both slow and pointless. Roots and intersections found here are
 * refined by bisection and then handed to V2's solver for an exact answer where
 * one exists (see [CasEngine.solve]).
 *
 * @param evaluate returns f(x) as a Double, or NaN where undefined
 */
class Plotter(private val evaluate: (Double) -> Double) {

    /**
     * Samples over [window], splitting the curve where it is undefined or makes
     * a jump too large to be a real slope at this scale.
     */
    fun sample(window: PlotWindow, samples: Int = DEFAULT_SAMPLES): List<List<PlotPoint>> {
        if (!window.isValid() || samples < 2) return emptyList()

        val step = window.width / (samples - 1)
        val segments = mutableListOf<List<PlotPoint>>()
        var current = mutableListOf<PlotPoint>()
        var previous: PlotPoint? = null

        for (i in 0 until samples) {
            val x = window.xMin + i * step
            val y = safeEvaluate(x)

            if (y == null) {
                if (current.size >= 2) segments += current
                current = mutableListOf()
                previous = null
                continue
            }

            val point = PlotPoint(x, y)
            if (previous != null && isDiscontinuity(previous, point, window)) {
                if (current.size >= 2) segments += current
                current = mutableListOf()
            }
            current += point
            previous = point
        }
        if (current.size >= 2) segments += current
        return segments
    }

    /**
     * A jump is treated as a break when the curve crosses the whole window in
     * one step *and* changes sign — the signature of a pole such as `tan(x)`,
     * as opposed to a merely steep but continuous climb.
     */
    private fun isDiscontinuity(from: PlotPoint, to: PlotPoint, window: PlotWindow): Boolean {
        val jump = kotlin.math.abs(to.y - from.y)
        val crossedWindow = jump > window.height * DISCONTINUITY_FACTOR
        val changedSign = (from.y > 0) != (to.y > 0)
        return crossedWindow && changedSign
    }

    /** Real roots in [window], found by sign change and refined by bisection. */
    fun roots(window: PlotWindow, samples: Int = DEFAULT_SAMPLES): List<Double> {
        if (!window.isValid()) return emptyList()
        val step = window.width / (samples - 1)
        val found = mutableListOf<Double>()

        var previousX = window.xMin
        var previousY = safeEvaluate(previousX)
        for (i in 1 until samples) {
            val x = window.xMin + i * step
            val y = safeEvaluate(x)
            if (previousY != null && y != null) {
                if (previousY == 0.0) found += previousX
                else if (crossesZero(previousY, y) && !isDiscontinuity(
                        PlotPoint(previousX, previousY), PlotPoint(x, y), window,
                    )
                ) {
                    bisect(previousX, x)?.let { found += it }
                }
            }
            previousX = x
            previousY = y
        }
        return found.distinctBy { kotlin.math.round(it / TOLERANCE) }
    }

    private fun crossesZero(a: Double, b: Double): Boolean = (a > 0) != (b > 0)

    /** Narrows a sign-change bracket to a root. */
    private fun bisect(low: Double, high: Double): Double? {
        var a = low
        var b = high
        var fa = safeEvaluate(a) ?: return null
        repeat(BISECTION_STEPS) {
            val mid = (a + b) / 2
            val fMid = safeEvaluate(mid) ?: return null
            if (fMid == 0.0) return mid
            if (crossesZero(fa, fMid)) {
                b = mid
            } else {
                a = mid
                fa = fMid
            }
        }
        return (a + b) / 2
    }

    /** f(x) as a finite Double, or null where it is undefined or blows up. */
    private fun safeEvaluate(x: Double): Double? {
        val y = try {
            evaluate(x)
        } catch (e: RuntimeException) {
            return null
        }
        return if (y.isFinite()) y else null
    }

    companion object {
        const val DEFAULT_SAMPLES = 480
        private const val BISECTION_STEPS = 60
        private const val TOLERANCE = 1e-9

        /** Fraction of window height a single step must jump to count as a break. */
        private const val DISCONTINUITY_FACTOR = 0.5

        /**
         * Intersections of two curves: the roots of their difference.
         */
        fun intersections(
            first: (Double) -> Double,
            second: (Double) -> Double,
            window: PlotWindow,
        ): List<PlotPoint> {
            val difference = Plotter { x -> first(x) - second(x) }
            return difference.roots(window).map { x -> PlotPoint(x, first(x)) }
        }
    }
}
