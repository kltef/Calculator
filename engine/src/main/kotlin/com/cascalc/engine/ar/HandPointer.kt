package com.cascalc.engine.ar

/** A point in whatever space the caller is working in. */
data class Point2(val x: Float, val y: Float)

/**
 * The 21 hand landmarks a hand-tracking model reports, in the model's own
 * index order. Only the ones needed to recognise pointing are named.
 */
data class HandLandmarks(val points: List<Point2>) {

    val wrist: Point2 get() = points[WRIST]
    val indexTip: Point2 get() = points[INDEX_TIP]
    val indexKnuckle: Point2 get() = points[INDEX_PIP]

    val isValid: Boolean get() = points.size == LANDMARK_COUNT

    /**
     * A finger counts as extended when its tip is further from the wrist than
     * its middle joint. Comparing against the wrist rather than using absolute
     * positions keeps this true whichever way the hand is turned.
     */
    fun isExtended(tip: Int, joint: Int): Boolean =
        distance(points[tip], wrist) > distance(points[joint], wrist) * EXTENSION_MARGIN

    companion object {
        const val LANDMARK_COUNT = 21
        const val WRIST = 0
        const val INDEX_PIP = 6
        const val INDEX_TIP = 8
        const val MIDDLE_PIP = 10
        const val MIDDLE_TIP = 12
        const val RING_PIP = 14
        const val RING_TIP = 16
        const val PINKY_PIP = 18
        const val PINKY_TIP = 20

        /** Tip must clear the joint by this factor to count as extended. */
        private const val EXTENSION_MARGIN = 1.05f

        fun distance(a: Point2, b: Point2): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
    }
}

/** What the hand is currently doing. */
sealed interface PointerState {
    data object Absent : PointerState
    /** A hand is visible but not pointing — an open or closed hand. */
    data object Idle : PointerState

    /**
     * Pointing at [position], with [dwellProgress] running 0..1 as the finger
     * is held still over a target.
     */
    data class Pointing(
        val position: Point2,
        val targetId: Long?,
        val dwellProgress: Float,
    ) : PointerState
}

/**
 * Turns hand landmarks into a pointing cursor, and decides what it selects.
 *
 * Selection is by **dwell** rather than by any gesture: the user holds their
 * fingertip over an equation for a moment. Pinches and taps are unreliable to
 * detect and easy to trigger by accident, and there is nothing to tap in the
 * air. Dwell also gives the UI something honest to show — a ring that fills —
 * so nothing is ever selected without warning.
 *
 * As with [EquationTracker], this is deliberately free of Android types: the
 * decisions are the part worth testing, and a camera is a poor test harness.
 */
class HandPointer(
    private val smoothing: Float = DEFAULT_SMOOTHING,
    private val dwellMillis: Long = DEFAULT_DWELL_MILLIS,
    private val reachFactor: Float = DEFAULT_REACH,
    /**
     * How many of the middle, ring and pinky fingers must read as curled.
     *
     * Requiring all three is too strict in practice: landmark noise, and the
     * way a relaxed hand leaves one finger half-out, make a perfectly ordinary
     * pointing pose fail the test and the cursor never appears. Two of three
     * keeps the gesture distinct from an open hand while tolerating that.
     */
    private val requiredCurled: Int = DEFAULT_REQUIRED_CURLED,
) {

    private var smoothed: Point2? = null
    private var dwellTargetId: Long? = null
    private var dwellStartedAt: Long = 0L
    private var lastSelected: Long? = null

    /**
     * Folds one frame of tracking into the pointer state.
     *
     * @param hand landmarks, or null when no hand is visible
     * @param targets things that can be pointed at, in the same coordinate space
     * @param nowMillis a monotonic clock
     */
    fun update(
        hand: HandLandmarks?,
        targets: List<TrackedEquation>,
        nowMillis: Long,
    ): PointerState {
        if (hand == null || !hand.isValid) {
            reset()
            return PointerState.Absent
        }
        if (!isPointing(hand)) {
            reset()
            return PointerState.Idle
        }

        val raw = projectedTip(hand)
        val position = smoothed?.let {
            Point2(
                it.x + (raw.x - it.x) * smoothing,
                it.y + (raw.y - it.y) * smoothing,
            )
        } ?: raw
        smoothed = position

        val target = targets.firstOrNull { contains(it.box, position) }
        if (target == null || target.id != dwellTargetId) {
            dwellTargetId = target?.id
            dwellStartedAt = nowMillis
            if (target == null) lastSelected = null
        }

        val held = if (dwellTargetId == null) 0L else nowMillis - dwellStartedAt
        val progress = if (dwellMillis <= 0) 1f
        else (held.toFloat() / dwellMillis).coerceIn(0f, 1f)

        return PointerState.Pointing(position, dwellTargetId, progress)
    }

    /**
     * The id to open, if a dwell has just completed.
     *
     * Returns a given target only once per dwell, so holding still does not
     * re-trigger every frame.
     */
    fun consumeSelection(state: PointerState): Long? {
        val pointing = state as? PointerState.Pointing ?: return null
        val id = pointing.targetId ?: return null
        if (pointing.dwellProgress < 1f) return null
        if (lastSelected == id) return null
        lastSelected = id
        return id
    }

    fun reset() {
        smoothed = null
        dwellTargetId = null
        dwellStartedAt = 0L
        lastSelected = null
    }

    /**
     * Pointing means the index extended with the other fingers mostly curled.
     * The thumb is ignored: it sits at an angle that makes "extended"
     * ambiguous, and people point comfortably with the thumb either way.
     */
    fun isPointing(hand: HandLandmarks): Boolean {
        if (!hand.isExtended(HandLandmarks.INDEX_TIP, HandLandmarks.INDEX_PIP)) return false
        val curled = listOf(
            HandLandmarks.MIDDLE_TIP to HandLandmarks.MIDDLE_PIP,
            HandLandmarks.RING_TIP to HandLandmarks.RING_PIP,
            HandLandmarks.PINKY_TIP to HandLandmarks.PINKY_PIP,
        ).count { (tip, joint) -> !hand.isExtended(tip, joint) }
        return curled >= requiredCurled
    }

    /**
     * The cursor sits a little beyond the fingertip, along the finger.
     *
     * Pointing *at* something means the finger occludes it, so a cursor exactly
     * on the fingertip lands on the user's own hand. Extending along the
     * knuckle-to-tip direction puts it where they are actually indicating.
     */
    private fun projectedTip(hand: HandLandmarks): Point2 {
        val tip = hand.indexTip
        val knuckle = hand.indexKnuckle
        return Point2(
            tip.x + (tip.x - knuckle.x) * reachFactor,
            tip.y + (tip.y - knuckle.y) * reachFactor,
        )
    }

    private fun contains(box: Box, point: Point2): Boolean =
        point.x >= box.left && point.x <= box.right &&
            point.y >= box.top && point.y <= box.bottom

    companion object {
        const val DEFAULT_SMOOTHING = 0.4f
        const val DEFAULT_DWELL_MILLIS = 700L
        const val DEFAULT_REACH = 0.6f
        const val DEFAULT_REQUIRED_CURLED = 2
    }
}
