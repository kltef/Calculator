package com.cascalc.engine.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandPointerTest {

    /**
     * Builds a hand with the wrist at the origin and each finger laid out along
     * +y, extended or curled as asked.
     */
    private fun hand(
        index: Boolean = true,
        middle: Boolean = false,
        ring: Boolean = false,
        pinky: Boolean = false,
        tipX: Float = 10f,
        tipY: Float = 10f,
    ): HandLandmarks {
        val points = MutableList(HandLandmarks.LANDMARK_COUNT) { Point2(0f, 0f) }
        // Joint at distance 5, tip beyond it when extended, short of it when not.
        fun finger(pip: Int, tip: Int, extended: Boolean, x: Float, y: Float) {
            points[pip] = Point2(x * 0.5f, y * 0.5f)
            points[tip] = if (extended) Point2(x, y) else Point2(x * 0.25f, y * 0.25f)
        }
        finger(HandLandmarks.INDEX_PIP, HandLandmarks.INDEX_TIP, index, tipX, tipY)
        finger(HandLandmarks.MIDDLE_PIP, HandLandmarks.MIDDLE_TIP, middle, 12f, 0f)
        finger(HandLandmarks.RING_PIP, HandLandmarks.RING_TIP, ring, 14f, 0f)
        finger(HandLandmarks.PINKY_PIP, HandLandmarks.PINKY_TIP, pinky, 16f, 0f)
        return HandLandmarks(points)
    }

    private fun target(id: Long, l: Float, t: Float, r: Float, b: Float) =
        TrackedEquation(id = id, text = "2+2", box = Box(l, t, r, b), solution = "4")

    // --- gesture ----------------------------------------------------------

    @Test fun `no hand means no pointer`() {
        val pointer = HandPointer()
        assertEquals(PointerState.Absent, pointer.update(null, emptyList(), 0L))
    }

    @Test fun `a relaxed point with one finger half-out still counts`() {
        // Landmark noise and ordinary hand posture leave one finger partly
        // extended; requiring all three curled made the cursor never appear.
        val pointer = HandPointer()
        val state = pointer.update(
            hand(index = true, middle = false, ring = true, pinky = false),
            emptyList(),
            0L,
        )
        assertTrue(state is PointerState.Pointing)
    }

    @Test fun `two fingers out is not pointing`() {
        val pointer = HandPointer()
        val state = pointer.update(
            hand(index = true, middle = true, ring = true, pinky = false),
            emptyList(),
            0L,
        )
        assertEquals(PointerState.Idle, state)
    }

    @Test fun `an open hand is not pointing`() {
        val pointer = HandPointer()
        val state = pointer.update(
            hand(index = true, middle = true, ring = true, pinky = true),
            emptyList(),
            0L,
        )
        assertEquals(PointerState.Idle, state)
    }

    @Test fun `a fist is not pointing`() {
        val pointer = HandPointer()
        val state = pointer.update(hand(index = false), emptyList(), 0L)
        assertEquals(PointerState.Idle, state)
    }

    @Test fun `index out and the rest curled is pointing`() {
        val pointer = HandPointer()
        val state = pointer.update(hand(), emptyList(), 0L)
        assertTrue(state is PointerState.Pointing)
    }

    @Test fun `an incomplete landmark set is ignored`() {
        val pointer = HandPointer()
        val truncated = HandLandmarks(List(5) { Point2(0f, 0f) })
        assertEquals(PointerState.Absent, pointer.update(truncated, emptyList(), 0L))
    }

    // --- cursor placement -------------------------------------------------

    @Test fun `the cursor sits beyond the fingertip, not on it`() {
        // Tip (10,0), knuckle (5,0), reach 0.6 -> 10 + 5*0.6 = 13
        val pointer = HandPointer(smoothing = 1f, reachFactor = 0.6f)
        val state = pointer.update(hand(tipX = 10f, tipY = 0f), emptyList(), 0L)
            as PointerState.Pointing
        assertEquals(13f, state.position.x, 0.001f)
    }

    @Test fun `the cursor eases toward the finger rather than jumping`() {
        // Both frames must be real pointing poses; a fingertip at the wrist is
        // degenerate and reads as not-extended.
        val pointer = HandPointer(smoothing = 0.5f, reachFactor = 0f)
        pointer.update(hand(tipX = 2f, tipY = 0f), emptyList(), 0L)
        val state = pointer.update(hand(tipX = 10f, tipY = 0f), emptyList(), 16L)
            as PointerState.Pointing
        // Halfway from 2 to 10.
        assertEquals(6f, state.position.x, 0.001f)
    }

    // --- dwell selection --------------------------------------------------

    @Test fun `dwell fills while held over a target`() {
        val pointer = HandPointer(smoothing = 1f, reachFactor = 0f, dwellMillis = 1000)
        val targets = listOf(target(7, 0f, -5f, 20f, 5f))

        val start = pointer.update(hand(tipX = 10f, tipY = 0f), targets, 0L) as PointerState.Pointing
        assertEquals(7L, start.targetId)
        assertEquals(0f, start.dwellProgress, 0.001f)

        val half = pointer.update(hand(tipX = 10f, tipY = 0f), targets, 500L) as PointerState.Pointing
        assertEquals(0.5f, half.dwellProgress, 0.001f)

        val full = pointer.update(hand(tipX = 10f, tipY = 0f), targets, 1000L) as PointerState.Pointing
        assertEquals(1f, full.dwellProgress, 0.001f)
    }

    @Test fun `selection fires once the dwell completes`() {
        val pointer = HandPointer(smoothing = 1f, reachFactor = 0f, dwellMillis = 500)
        val targets = listOf(target(7, 0f, -5f, 20f, 5f))

        pointer.update(hand(tipX = 10f, tipY = 0f), targets, 0L)
        val early = pointer.update(hand(tipX = 10f, tipY = 0f), targets, 200L)
        assertNull(pointer.consumeSelection(early))

        val done = pointer.update(hand(tipX = 10f, tipY = 0f), targets, 600L)
        assertEquals(7L, pointer.consumeSelection(done))
    }

    @Test fun `holding still does not re-trigger the same target`() {
        val pointer = HandPointer(smoothing = 1f, reachFactor = 0f, dwellMillis = 500)
        val targets = listOf(target(7, 0f, -5f, 20f, 5f))

        pointer.update(hand(tipX = 10f, tipY = 0f), targets, 0L)
        val done = pointer.update(hand(tipX = 10f, tipY = 0f), targets, 600L)
        assertEquals(7L, pointer.consumeSelection(done))

        val stillHeld = pointer.update(hand(tipX = 10f, tipY = 0f), targets, 900L)
        assertNull(pointer.consumeSelection(stillHeld))
    }

    @Test fun `moving off a target restarts the dwell`() {
        val pointer = HandPointer(smoothing = 1f, reachFactor = 0f, dwellMillis = 500)
        val targets = listOf(target(7, 0f, -5f, 20f, 5f))

        pointer.update(hand(tipX = 10f, tipY = 0f), targets, 0L)
        // Off the target.
        pointer.update(hand(tipX = 500f, tipY = 0f), targets, 300L)
        // Back on: the clock starts again, so 200ms more is not enough.
        pointer.update(hand(tipX = 10f, tipY = 0f), targets, 400L)
        val soon = pointer.update(hand(tipX = 10f, tipY = 0f), targets, 600L)
        assertNull(pointer.consumeSelection(soon))
    }

    @Test fun `pointing at nothing selects nothing`() {
        val pointer = HandPointer(smoothing = 1f, reachFactor = 0f, dwellMillis = 100)
        val targets = listOf(target(7, 100f, 100f, 200f, 200f))
        val state = pointer.update(hand(tipX = 10f, tipY = 0f), targets, 0L) as PointerState.Pointing
        assertNull(state.targetId)
        assertNull(pointer.consumeSelection(state))
    }

    @Test fun `lowering the hand clears the dwell`() {
        val pointer = HandPointer(smoothing = 1f, reachFactor = 0f, dwellMillis = 500)
        val targets = listOf(target(7, 0f, -5f, 20f, 5f))
        pointer.update(hand(tipX = 10f, tipY = 0f), targets, 0L)
        pointer.update(null, targets, 200L)
        // The dwell must start over, not resume where it left off.
        pointer.update(hand(tipX = 10f, tipY = 0f), targets, 400L)
        val soon = pointer.update(hand(tipX = 10f, tipY = 0f), targets, 700L)
        assertNull(pointer.consumeSelection(soon))
    }
}
