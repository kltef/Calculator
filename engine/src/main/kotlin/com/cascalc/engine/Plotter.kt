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

/** A shaded region under a curve, with its signed area. */
data class AreaUnderCurve(val points: List<PlotPoint>, val area: Double)

/** A straight line in the form y = slope·x + intercept. */
data class TangentLine(val slope: Double, val intercept: Double, val at: PlotPoint) {
    fun valueAt(x: Double): Double = slope * x + intercept
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

    /**
     * Samples a parametric pair (x(t), y(t)) over t.
     *
     * Unlike [sample] this can revisit the same x, so it is not a function of x
     * and cannot be split on x-monotonicity; breaks come only from undefined
     * points.
     */
    fun sampleParametric(
        yOf: (Double) -> Double,
        tMin: Double,
        tMax: Double,
        samples: Int = DEFAULT_SAMPLES,
    ): List<List<PlotPoint>> {
        if (samples < 2 || tMax <= tMin) return emptyList()
        val step = (tMax - tMin) / (samples - 1)
        val segments = mutableListOf<List<PlotPoint>>()
        var current = mutableListOf<PlotPoint>()

        for (i in 0 until samples) {
            val t = tMin + i * step
            val x = safeEvaluate(t)
            val y = try {
                yOf(t).takeIf { it.isFinite() }
            } catch (e: RuntimeException) {
                null
            }
            if (x == null || y == null) {
                if (current.size >= 2) segments += current
                current = mutableListOf()
                continue
            }
            current += PlotPoint(x, y)
        }
        if (current.size >= 2) segments += current
        return segments
    }

    /**
     * Samples a polar curve r(θ), converting to Cartesian.
     *
     * Negative r is kept rather than clamped: r = cos(2θ) draws its full rose
     * only if negative radii are plotted opposite their angle, which is what
     * the conversion does naturally.
     */
    fun samplePolar(
        thetaMin: Double = 0.0,
        thetaMax: Double = 2 * Math.PI,
        samples: Int = DEFAULT_SAMPLES,
    ): List<List<PlotPoint>> {
        if (samples < 2 || thetaMax <= thetaMin) return emptyList()
        val step = (thetaMax - thetaMin) / (samples - 1)
        val segments = mutableListOf<List<PlotPoint>>()
        var current = mutableListOf<PlotPoint>()

        for (i in 0 until samples) {
            val theta = thetaMin + i * step
            val r = safeEvaluate(theta)
            if (r == null) {
                if (current.size >= 2) segments += current
                current = mutableListOf()
                continue
            }
            current += PlotPoint(r * kotlin.math.cos(theta), r * kotlin.math.sin(theta))
        }
        if (current.size >= 2) segments += current
        return segments
    }

    /**
     * The region between the curve and the x-axis over [from]..[to], as a
     * closed polygon ready to fill, plus its signed area.
     *
     * Area is by Simpson's rule, which is exact for anything up to a cubic and
     * close enough for drawing elsewhere. It is *signed*: area below the axis
     * counts negative, matching what the definite integral means.
     */
    fun areaUnder(from: Double, to: Double, samples: Int = AREA_SAMPLES): AreaUnderCurve? {
        if (to <= from || samples < 2) return null
        // Simpson's rule needs an even number of intervals.
        val intervals = if (samples % 2 == 0) samples else samples + 1
        val step = (to - from) / intervals

        val points = mutableListOf<PlotPoint>()
        var total = 0.0
        for (i in 0..intervals) {
            val x = from + i * step
            val y = safeEvaluate(x) ?: return null
            points += PlotPoint(x, y)
            val weight = when {
                i == 0 || i == intervals -> 1.0
                i % 2 == 1 -> 4.0
                else -> 2.0
            }
            total += weight * y
        }
        return AreaUnderCurve(points, total * step / 3.0)
    }

    companion object {
        const val DEFAULT_SAMPLES = 480
        private const val AREA_SAMPLES = 200
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
