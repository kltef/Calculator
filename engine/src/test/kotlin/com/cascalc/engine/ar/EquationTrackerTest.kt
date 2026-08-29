package com.cascalc.engine.ar

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquationTrackerTest {

    private fun box(l: Float, t: Float, r: Float, b: Float) = Box(l, t, r, b)
    private fun detection(text: String, l: Float, t: Float, r: Float, b: Float) =
        DetectedText(text, box(l, t, r, b))

    // --- matching ---------------------------------------------------------

    @Test fun `a new equation becomes a track`() {
        val tracker = EquationTracker()
        val tracks = tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 5f)))
        assertEquals(1, tracks.size)
        assertEquals("2+2", tracks.first().text)
    }

    @Test fun `the same equation keeps its identity as the camera moves`() {
        val tracker = EquationTracker()
        val first = tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 5f))).first()
        val second = tracker.update(listOf(detection("2+2", 1f, 1f, 11f, 6f))).first()
        assertEquals(first.id, second.id)
    }

    @Test fun `two equations on the page stay separate`() {
        val tracker = EquationTracker()
        val tracks = tracker.update(
            listOf(
                detection("2+2", 0f, 0f, 10f, 5f),
                detection("3+3", 0f, 50f, 10f, 55f),
            ),
        )
        assertEquals(2, tracks.size)
        assertEquals(2, tracks.map { it.id }.distinct().size)
    }

    @Test fun `identical text elsewhere on the page does not steal a track`() {
        // Position decides identity; text only breaks ties. Otherwise "2+2" at
        // the top of the page captures the track from "2+2" at the bottom and
        // the overlay jumps across the page.
        val tracker = EquationTracker()
        val first = tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 5f))).first()
        val tracks = tracker.update(listOf(detection("2+2", 500f, 500f, 510f, 505f)))

        assertTrue(tracks.none { it.id == first.id && it.misses == 0 })
        assertTrue(tracks.any { it.id != first.id })
    }

    // --- stability, the roadmap's named risk ------------------------------

    @Test fun `the drawn box eases toward the detection rather than snapping`() {
        // A frame-to-frame camera movement: the boxes still overlap, which is
        // what makes it the same equation rather than a new one.
        val tracker = EquationTracker(smoothing = 0.5f)
        tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 10f)))
        val moved = tracker.update(listOf(detection("2+2", 4f, 0f, 14f, 10f))).first()
        // Halfway, not all the way.
        assertEquals(2f, moved.box.left, 0.001f)
    }

    @Test fun `smoothing converges on the real position when the camera settles`() {
        val tracker = EquationTracker(smoothing = 0.5f)
        tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 10f)))
        repeat(20) { tracker.update(listOf(detection("2+2", 4f, 0f, 14f, 10f))) }
        assertTrue(abs(tracker.active.first().box.left - 4f) < 0.01f)
    }

    @Test fun `a track survives a dropped frame`() {
        val tracker = EquationTracker(graceFrames = 2)
        val first = tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 5f))).first()
        val afterMiss = tracker.update(emptyList())
        assertEquals(1, afterMiss.size)
        assertEquals(first.id, afterMiss.first().id)
    }

    @Test fun `a track is dropped once it has been gone too long`() {
        val tracker = EquationTracker(graceFrames = 2)
        tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 5f)))
        repeat(3) { tracker.update(emptyList()) }
        assertTrue(tracker.active.isEmpty())
    }

    @Test fun `a recovered track resumes rather than restarting`() {
        val tracker = EquationTracker(graceFrames = 3)
        val first = tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 5f))).first()
        tracker.update(emptyList())
        val recovered = tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 5f))).first()
        assertEquals(first.id, recovered.id)
        assertEquals(0, recovered.misses)
    }

    // --- solutions --------------------------------------------------------

    @Test fun `a solution survives the equation being re-read`() {
        val tracker = EquationTracker()
        val track = tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 5f))).first()
        tracker.attachSolution(track.id, "4")

        val next = tracker.update(listOf(detection("2+2", 1f, 0f, 11f, 5f))).first()
        assertEquals("4", next.solution)
    }

    @Test fun `changing the equation clears its stale answer`() {
        val tracker = EquationTracker()
        val track = tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 5f))).first()
        tracker.attachSolution(track.id, "4")

        // Same place on the page, different equation: the old answer must go.
        val next = tracker.update(listOf(detection("2+3", 0f, 0f, 10f, 5f))).first()
        assertNull(next.solution)
        assertEquals("2+3", next.text)
    }

    @Test fun `whitespace changes do not count as a different equation`() {
        val tracker = EquationTracker()
        val track = tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 5f))).first()
        tracker.attachSolution(track.id, "4")
        val next = tracker.update(listOf(detection("2 + 2", 0f, 0f, 10f, 5f))).first()
        assertEquals("4", next.solution)
    }

    @Test fun `reset clears everything`() {
        val tracker = EquationTracker()
        tracker.update(listOf(detection("2+2", 0f, 0f, 10f, 5f)))
        tracker.reset()
        assertTrue(tracker.active.isEmpty())
    }

    // --- geometry ---------------------------------------------------------

    @Test fun `overlap is zero for disjoint boxes and one for identical ones`() {
        assertEquals(0f, box(0f, 0f, 1f, 1f).overlapWith(box(5f, 5f, 6f, 6f)), 0.0001f)
        assertEquals(1f, box(0f, 0f, 2f, 2f).overlapWith(box(0f, 0f, 2f, 2f)), 0.0001f)
    }

    @Test fun `overlap of half-covering boxes is a third`() {
        // Intersection 1x2, union 3x2 -> 1/3
        assertEquals(1f / 3f, box(0f, 0f, 2f, 2f).overlapWith(box(1f, 0f, 3f, 2f)), 0.0001f)
    }
}
