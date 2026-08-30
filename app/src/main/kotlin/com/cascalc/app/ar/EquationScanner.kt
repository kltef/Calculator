package com.cascalc.app.ar

import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.cascalc.engine.ar.HandLandmarks

/**
 * Runs a wrapped analyzer only every [intervalMillis], closing frames in
 * between.
 *
 * The roadmap calls for interval-based recognition rather than per-frame, and
 * this is where that is enforced: text recognition on every frame of a preview
 * stream drains the battery and heats the phone for no benefit, since
 * handwriting on a page does not change 30 times a second. Skipped frames are
 * closed immediately — an unclosed [ImageProxy] stalls the whole pipeline.
 */
class ThrottledAnalyzer(
    private val delegate: ImageAnalysis.Analyzer,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ImageAnalysis.Analyzer {

    private var lastRun = 0L

    override fun analyze(image: ImageProxy) {
        val now = clock()
        if (now - lastRun < intervalMillis) {
            image.close()
            return
        }
        lastRun = now
        delegate.analyze(image)
    }

    // Both must be forwarded, or CameraX will not apply the view-referenced
    // coordinate transform that keeps overlays aligned with the preview.
    override fun getTargetCoordinateSystem(): Int = delegate.targetCoordinateSystem

    override fun updateTransform(matrix: Matrix?) {
        delegate.updateTransform(matrix)
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 400L
    }
}

/**
 * Runs hand detection on every frame, then hands the same frame to the text
 * analyzer.
 *
 * The two run at different rates on purpose. A pointing cursor has to keep up
 * with the finger, so hands are read every frame; handwriting on a page does
 * not move, so text stays on its interval (the delegate throttles itself).
 *
 * The delegate closes the frame, so hand detection must finish with it first.
 */
class HandAndTextAnalyzer(
    private val delegate: ImageAnalysis.Analyzer,
    private val detectHand: (ImageProxy) -> HandLandmarks?,
    private val onHand: (HandLandmarks?, Matrix?) -> Unit,
) : ImageAnalysis.Analyzer {

    private var transform: Matrix? = null

    override fun analyze(image: ImageProxy) {
        val hand = try {
            detectHand(image)
        } catch (e: RuntimeException) {
            null
        }
        onHand(hand, transform)
        delegate.analyze(image)
    }

    override fun getTargetCoordinateSystem(): Int = delegate.targetCoordinateSystem

    /**
     * CameraX supplies the sensor-to-view transform here. It is captured for
     * mapping hand landmarks as well as forwarded, since both overlays have to
     * land in the same coordinate space to be compared.
     */
    override fun updateTransform(matrix: Matrix?) {
        transform = matrix
        delegate.updateTransform(matrix)
    }
}
