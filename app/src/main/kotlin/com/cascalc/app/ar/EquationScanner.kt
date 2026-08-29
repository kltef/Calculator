package com.cascalc.app.ar

import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

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
