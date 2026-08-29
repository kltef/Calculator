package com.cascalc.engine.ar

/** A rectangle in image coordinates, as OCR reports it. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
    val area: Float get() = maxOf(0f, width) * maxOf(0f, height)

    /** Intersection over union — how much two boxes overlap, 0..1. */
    fun overlapWith(other: Box): Float {
        val left = maxOf(this.left, other.left)
        val top = maxOf(this.top, other.top)
        val right = minOf(this.right, other.right)
        val bottom = minOf(this.bottom, other.bottom)
        if (right <= left || bottom <= top) return 0f
        val intersection = (right - left) * (bottom - top)
        val union = area + other.area - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    /** Moves a fraction [t] of the way toward [target]. */
    fun lerpTo(target: Box, t: Float): Box = Box(
        left + (target.left - left) * t,
        top + (target.top - top) * t,
        right + (target.right - right) * t,
        bottom + (target.bottom - bottom) * t,
    )
}

/** One line of text OCR found in a frame. */
data class DetectedText(val text: String, val box: Box)

/**
 * An equation being followed across frames.
 *
 * @param solution filled in once the engine has answered; null while pending
 * @param misses consecutive frames this track was not seen in
 */
data class TrackedEquation(
    val id: Long,
    val text: String,
    val box: Box,
    val solution: String? = null,
    val misses: Int = 0,
    val settled: Boolean = false,
)

/**
 * Follows recognised equations from frame to frame.
 *
 * This is the part the roadmap flags as the biggest risk, so it is deliberately
 * plain Kotlin with no Android types — it can be tested directly rather than
 * only observed through a camera.
 *
 * Three things keep an overlay from jittering:
 *
 *  - **Matching by overlap and text**, so a track survives OCR re-reporting the
 *    same line with slightly different bounds each frame.
 *  - **Smoothing** — the drawn box eases toward the detection instead of
 *    snapping, which is what turns a twitching label into a steady one.
 *  - **Grace frames** — a track that disappears for a frame or two is kept, so
 *    a momentary OCR failure (hand shadow, motion blur) does not make the
 *    answer flicker away and back.
 */
class EquationTracker(
    private val smoothing: Float = DEFAULT_SMOOTHING,
    private val minOverlap: Float = MIN_OVERLAP,
    private val graceFrames: Int = GRACE_FRAMES,
) {

    private var nextId = 1L
    private var tracks: List<TrackedEquation> = emptyList()

    val active: List<TrackedEquation> get() = tracks

    /**
     * Folds one frame's detections into the tracks.
     *
     * @return the tracks that should currently be drawn
     */
    fun update(detections: List<DetectedText>): List<TrackedEquation> {
        val unmatched = detections.toMutableList()
        val updated = mutableListOf<TrackedEquation>()

        for (track in tracks) {
            val match = unmatched
                .map { it to score(track, it) }
                .filter { it.second >= minOverlap }
                .maxByOrNull { it.second }
                ?.first

            if (match == null) {
                // Keep it briefly; drop it once it has been gone too long.
                if (track.misses + 1 <= graceFrames) {
                    updated += track.copy(misses = track.misses + 1)
                }
                continue
            }

            unmatched -= match
            val sameText = normalize(match.text) == normalize(track.text)
            updated += track.copy(
                // Re-reading the same equation must not discard its solution.
                text = if (sameText) track.text else match.text,
                box = track.box.lerpTo(match.box, smoothing),
                solution = if (sameText) track.solution else null,
                settled = sameText && track.settled,
                misses = 0,
            )
        }

        for (detection in unmatched) {
            updated += TrackedEquation(id = nextId++, text = detection.text, box = detection.box)
        }

        tracks = updated
        return tracks
    }

    /** Records the engine's answer for a track. */
    fun attachSolution(id: Long, solution: String?) {
        tracks = tracks.map {
            if (it.id == id) it.copy(solution = solution, settled = true) else it
        }
    }

    fun reset() {
        tracks = emptyList()
    }

    /**
     * How well a detection matches a track.
     *
     * Position decides *whether* two things can be the same equation; text only
     * breaks ties between candidates that already overlap. Letting the text
     * bonus stand alone would match identical writing anywhere on the page —
     * `2+2` at the top would capture the track from `2+2` at the bottom and the
     * overlay would jump.
     */
    private fun score(track: TrackedEquation, detection: DetectedText): Float {
        val overlap = track.box.overlapWith(detection.box)
        if (overlap < minOverlap) return 0f
        val textMatches = normalize(track.text) == normalize(detection.text)
        return if (textMatches) minOf(1f, overlap + TEXT_MATCH_BONUS) else overlap
    }

    private fun normalize(text: String): String = text.filterNot { it.isWhitespace() }

    private companion object {
        /** Fraction of the way to the new position each frame. */
        const val DEFAULT_SMOOTHING = 0.35f
        const val MIN_OVERLAP = 0.2f
        const val GRACE_FRAMES = 3
        const val TEXT_MATCH_BONUS = 0.25f
    }
}
