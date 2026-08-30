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

    /** Either a working detector, or why there isn't one. */
    sealed interface Outcome {
        data class Ready(val detector: HandDetector) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    companion object {
        /**
         * Builds a detector, reporting *why* if it cannot.
         *
         * The reason is carried out rather than swallowed: a failure here means
         * something specific — a missing native library, a corrupt model, a
         * class the shrinker removed — and each has a different fix. Returning
         * a bare null turns all of them into the same useless "unavailable".
         */
        fun create(context: Context, model: ByteBuffer): Outcome = try {
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
            Outcome.Ready(HandDetector(HandLandmarker.createFromOptions(context, options)))
        } catch (e: Throwable) {
            // Model corrupt, native library missing, unsupported device: all of
            // these mean "no hand tracking", never "no AR mode".
            var root: Throwable = e
            while (root.cause != null && root.cause !== root) root = root.cause!!
            // The device ABI is included because the commonest silent cause is
            // a build that ships no native library for this architecture, and
            // that is otherwise indistinguishable from a code fault.
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            Outcome.Failed(
                "${root::class.java.simpleName}: ${root.message ?: "no detail"} [abi $abi]",
            )
        }

        private const val MIN_CONFIDENCE = 0.5f
    }
}
