package com.cascalc.engine.ar

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGeometryTest {

    private val width = 640
    private val height = 480

    @Test fun `no rotation passes coordinates through`() {
        val (x, y) = ImageGeometry.toSensorPixels(0.5f, 0.25f, width, height, 0)
        assertEquals(320f, x, 0.01f)
        assertEquals(120f, y, 0.01f)
    }

    @Test fun `ninety degrees maps the upright frame back onto the sensor`() {
        // Upright frame is 480x640 when the sensor is rotated 90 degrees.
        val (x, y) = ImageGeometry.toSensorPixels(0f, 0f, width, height, 90)
        assertEquals(0f, x, 0.01f)
        assertEquals(480f, y, 0.01f)
    }

    @Test fun `a hundred and eighty degrees mirrors both axes`() {
        val (x, y) = ImageGeometry.toSensorPixels(0.25f, 0.25f, width, height, 180)
        assertEquals(480f, x, 0.01f)
        assertEquals(360f, y, 0.01f)
    }

    @Test fun `two hundred and seventy degrees is the inverse of ninety`() {
        val (x, y) = ImageGeometry.toSensorPixels(0f, 0f, width, height, 270)
        assertEquals(640f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }

    @Test fun `the centre stays the centre under every rotation`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val (x, y) = ImageGeometry.toSensorPixels(0.5f, 0.5f, width, height, rotation)
            assertEquals("rotation $rotation", width / 2f, x, 0.01f)
            assertEquals("rotation $rotation", height / 2f, y, 0.01f)
        }
    }

    @Test fun `negative and oversized rotations are normalised`() {
        val expected = ImageGeometry.toSensorPixels(0.3f, 0.7f, width, height, 90)
        assertEquals(expected, ImageGeometry.toSensorPixels(0.3f, 0.7f, width, height, -270))
        assertEquals(expected, ImageGeometry.toSensorPixels(0.3f, 0.7f, width, height, 450))
    }
}
