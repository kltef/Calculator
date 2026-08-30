package com.cascalc.app.ar

import android.content.Context
import android.graphics.Bitmap
import com.cascalc.engine.ar.HandLandmarks
import com.cascalc.engine.ar.ImageGeometry
import com.cascalc.engine.ar.Point2
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.nio.ByteBuffer

/**
 * Wraps MediaPipe's hand landmarker.
 *
 * Landmarks come back normalised against the *upright* frame, so they are
 * converted to sensor pixels here (see [ImageGeometry], which is tested
 * separately); mapping those into view coordinates is the caller's job, using
 * the transform CameraX supplies.
 */
class HandDetector private constructor(private val landmarker: HandLandmarker) {

    /**
     * @return landmarks in sensor-pixel coordinates, or null if no hand is
     *   visible or detection failed
     */
    fun detect(bitmap: Bitmap, rotationDegrees: Int): HandLandmarks? {
        val image = BitmapImageBuilder(bitmap).build()
        val options = ImageProcessingOptions.builder()
            .setRotationDegrees(rotationDegrees)
            .build()

        val result = try {
            landmarker.detect(image, options)
        } catch (e: RuntimeException) {
            return null
        }

        val hand = result.landmarks().firstOrNull() ?: return null
        if (hand.size != HandLandmarks.LANDMARK_COUNT) return null

        return HandLandmarks(
            hand.map { landmark ->
                val (x, y) = ImageGeometry.toSensorPixels(
                    landmark.x(),
                    landmark.y(),
                    bitmap.width,
                    bitmap.height,
                    rotationDegrees,
                )
                Point2(x, y)
            },
        )
    }

    fun close() {
        runCatching { landmarker.close() }
    }

    companion object {
        /** @return null when the model cannot be loaded; tracking is optional. */
        fun create(context: Context, model: ByteBuffer): HandDetector? = try {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(model).build())
                .setRunningMode(RunningMode.IMAGE)
                // One hand: pointing is one-handed, and a second hand only adds
                // work and ambiguity about which one is the cursor.
                .setNumHands(1)
                .setMinHandDetectionConfidence(MIN_CONFIDENCE)
                .setMinHandPresenceConfidence(MIN_CONFIDENCE)
                .setMinTrackingConfidence(MIN_CONFIDENCE)
                .build()
            HandDetector(HandLandmarker.createFromOptions(context, options))
        } catch (e: Throwable) {
            // Model corrupt, native library missing, unsupported device: all of
            // these mean "no hand tracking", never "no AR mode".
            null
        }

        private const val MIN_CONFIDENCE = 0.5f
    }
}
