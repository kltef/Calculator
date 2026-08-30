package com.cascalc.engine.ar

/**
 * Coordinate maths for camera frames.
 *
 * Hand-landmark models report points normalised (0..1) against the *upright*
 * image — the frame after the camera's rotation has been applied. Overlays,
 * meanwhile, are positioned using a transform that expects raw sensor
 * coordinates. Getting this backwards puts the cursor on the wrong axis, or
 * mirrored, which is the usual reason a pointing cursor is confidently in the
 * wrong place.
 *
 * Kept here, away from Android, so the rotations can be tested directly.
 */
object ImageGeometry {

    /**
     * Converts a point normalised in the upright frame to pixel coordinates in
     * the unrotated sensor image.
     *
     * @param rotationDegrees clockwise rotation applied to make the image
     *   upright — 0, 90, 180 or 270
     * @param imageWidth width of the *unrotated* image
     * @param imageHeight height of the *unrotated* image
     */
    fun toSensorPixels(
        normalizedX: Float,
        normalizedY: Float,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
    ): Pair<Float, Float> {
        val rotation = ((rotationDegrees % 360) + 360) % 360

        // Dimensions of the upright frame the normalised point refers to.
        val uprightWidth = if (rotation == 90 || rotation == 270) imageHeight else imageWidth
        val uprightHeight = if (rotation == 90 || rotation == 270) imageWidth else imageHeight

        val x = normalizedX * uprightWidth
        val y = normalizedY * uprightHeight

        return when (rotation) {
            90 -> y to (uprightWidth - x)
            180 -> (uprightWidth - x) to (uprightHeight - y)
            270 -> (uprightHeight - y) to x
            else -> x to y
        }
    }
}
