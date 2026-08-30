package com.cascalc.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cascalc.app.GraphMarker
import com.cascalc.engine.AreaUnderCurve
import com.cascalc.engine.PlotCurve
import com.cascalc.engine.PlotWindow
import com.cascalc.engine.TangentLine
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Distinct, colour-blind-safe-ish series colours, matched by index to curves. */
val CurveColors = listOf(
    Color(0xFF3B82F6),
    Color(0xFFEF4444),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
)

/**
 * Draws the plotted curves with axes, gridlines, pan/zoom and tap-to-trace.
 *
 * Sampling happens in the ViewModel against a window in graph coordinates; this
 * only maps those to pixels, so zooming re-samples rather than stretching an
 * image, and curves stay smooth at any magnification.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun GraphCanvas(
    window: PlotWindow,
    curves: List<PlotCurve>,
    markers: List<GraphMarker>,
    traceEnabled: Boolean,
    onTransform: (panX: Double, panY: Double, zoom: Double, focusX: Double, focusY: Double) -> Unit,
    onTrace: (Double) -> Unit,
    modifier: Modifier = Modifier,
    tangent: TangentLine? = null,
    area: AreaUnderCurve? = null,
    /** 0..1; curves are revealed along their length as this animates up. */
    reveal: Float = 1f,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val gridColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val markerColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(window) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val unitsPerPixelX = window.width / size.width
                    val unitsPerPixelY = window.height / size.height
                    val focusX = window.xMin + centroid.x * unitsPerPixelX
                    val focusY = window.yMax - centroid.y * unitsPerPixelY
                    onTransform(
                        -pan.x * unitsPerPixelX,
                        pan.y * unitsPerPixelY,
                        zoom.toDouble(),
                        focusX,
                        focusY,
                    )
                }
            }
            .pointerInput(window, traceEnabled) {
                if (!traceEnabled) return@pointerInput
                detectTapGestures { offset ->
                    onTrace(window.xMin + offset.x * (window.width / size.width))
                }
            },
    ) {
        drawGrid(window, gridColor, axisColor, textMeasurer)
        // Shading sits under the curves so the line stays readable on top.
        area?.let { drawArea(it, window, CurveColors[0].copy(alpha = 0.25f)) }
        curves.forEachIndexed { index, curve ->
            drawCurve(curve, window, CurveColors[index % CurveColors.size], reveal)
        }
        tangent?.let { drawTangent(it, window, markerColor) }
        markers.forEach { marker ->
            drawMarker(marker, window, markerColor, textMeasurer)
        }
    }
}

private fun DrawScope.graphToScreen(x: Double, y: Double, window: PlotWindow): Offset = Offset(
    x = ((x - window.xMin) / window.width * size.width).toFloat(),
    y = ((window.yMax - y) / window.height * size.height).toFloat(),
)

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawGrid(
    window: PlotWindow,
    gridColor: Color,
    axisColor: Color,
    textMeasurer: TextMeasurer,
) {
    val xStep = niceStep(window.width)
    val yStep = niceStep(window.height)
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)

    var x = ceilTo(window.xMin, xStep)
    while (x <= window.xMax) {
        val screenX = graphToScreen(x, 0.0, window).x
        drawLine(gridColor, Offset(screenX, 0f), Offset(screenX, size.height), strokeWidth = 1f)
        x += xStep
    }
    var y = ceilTo(window.yMin, yStep)
    while (y <= window.yMax) {
        val screenY = graphToScreen(0.0, y, window).y
        drawLine(gridColor, Offset(0f, screenY), Offset(size.width, screenY), strokeWidth = 1f)
        y += yStep
    }

    // Axes, drawn last so they sit above the grid; clamped so they stay visible
    // when the origin is scrolled off-screen.
    val originY = graphToScreen(0.0, 0.0, window).y.coerceIn(0f, size.height)
    val originX = graphToScreen(0.0, 0.0, window).x.coerceIn(0f, size.width)
    drawLine(axisColor, Offset(0f, originY), Offset(size.width, originY), strokeWidth = 2f)
    drawLine(axisColor, Offset(originX, 0f), Offset(originX, size.height), strokeWidth = 2f)

    // Label a few ticks rather than every one, which would be unreadable.
    var labelX = ceilTo(window.xMin, xStep * LABEL_EVERY)
    while (labelX <= window.xMax) {
        if (abs(labelX) > xStep / 2) {
            val screenX = graphToScreen(labelX, 0.0, window).x
            drawText(
                textMeasurer,
                formatTick(labelX, xStep),
                topLeft = Offset(screenX + 4f, originY + 4f),
                style = labelStyle,
            )
        }
        labelX += xStep * LABEL_EVERY
    }
    var labelY = ceilTo(window.yMin, yStep * LABEL_EVERY)
    while (labelY <= window.yMax) {
        if (abs(labelY) > yStep / 2) {
            val screenY = graphToScreen(0.0, labelY, window).y
            drawText(
                textMeasurer,
                formatTick(labelY, yStep),
                topLeft = Offset(originX + 4f, screenY + 2f),
                style = labelStyle,
            )
        }
        labelY += yStep * LABEL_EVERY
    }
}

private fun DrawScope.drawCurve(
    curve: PlotCurve,
    window: PlotWindow,
    color: Color,
    reveal: Float = 1f,
) {
    for (segment in curve.segments) {
        if (segment.size < 2) continue
        // Draw only the leading fraction, so a new curve sweeps in rather than
        // appearing all at once.
        val visible = segment.take(
            (segment.size * reveal.coerceIn(0f, 1f)).toInt().coerceAtLeast(2),
        )
        val path = Path()
        var started = false
        for (point in visible) {
            // Clamp far-off-screen values so a near-vertical run does not blow
            // up the path; the segment split already handles true poles.
            val clampedY = point.y.coerceIn(
                window.yMin - window.height * OFFSCREEN_SLACK,
                window.yMax + window.height * OFFSCREEN_SLACK,
            )
            val screen = graphToScreen(point.x, clampedY, window)
            if (!started) {
                path.moveTo(screen.x, screen.y)
                started = true
            } else {
                path.lineTo(screen.x, screen.y)
            }
        }
        drawPath(path, color, style = Stroke(width = 2.5f))
    }
}

/** Fills between the curve and the x-axis. */
private fun DrawScope.drawArea(area: AreaUnderCurve, window: PlotWindow, color: Color) {
    if (area.points.size < 2) return
    val path = Path()
    val first = area.points.first()
    path.moveTo(graphToScreen(first.x, 0.0, window).x, graphToScreen(first.x, 0.0, window).y)
    for (point in area.points) {
        val screen = graphToScreen(point.x, point.y, window)
        path.lineTo(screen.x, screen.y)
    }
    val last = area.points.last()
    path.lineTo(graphToScreen(last.x, 0.0, window).x, graphToScreen(last.x, 0.0, window).y)
    path.close()
    drawPath(path, color)
}

/** The tangent, drawn across the window with a dot at the point of contact. */
private fun DrawScope.drawTangent(tangent: TangentLine, window: PlotWindow, color: Color) {
    val start = graphToScreen(window.xMin, tangent.valueAt(window.xMin), window)
    val end = graphToScreen(window.xMax, tangent.valueAt(window.xMax), window)
    drawLine(color, start, end, strokeWidth = 2f)
    drawCircle(color, radius = 7f, center = graphToScreen(tangent.at.x, tangent.at.y, window))
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawMarker(
    marker: GraphMarker,
    window: PlotWindow,
    color: Color,
    textMeasurer: TextMeasurer,
) {
    val screen = graphToScreen(marker.point.x, marker.point.y, window)
    if (screen.x < 0 || screen.x > size.width) return
    drawCircle(color, radius = 6f, center = screen)
    drawText(
        textMeasurer,
        marker.label,
        topLeft = Offset(screen.x + 8f, screen.y - 28f),
        style = TextStyle(fontSize = 11.sp, color = color),
    )
}

/** A round step (1, 2 or 5 times a power of ten) giving roughly 10 divisions. */
private fun niceStep(span: Double): Double {
    if (span <= 0 || !span.isFinite()) return 1.0
    val rough = span / 10
    val magnitude = 10.0.pow(floor(log10(rough)))
    val normalized = rough / magnitude
    val factor = when {
        normalized < 1.5 -> 1.0
        normalized < 3.5 -> 2.0
        normalized < 7.5 -> 5.0
        else -> 10.0
    }
    return factor * magnitude
}

private fun ceilTo(value: Double, step: Double): Double = kotlin.math.ceil(value / step) * step

/** Ticks show only as many decimals as the step actually distinguishes. */
private fun formatTick(value: Double, step: Double): String {
    val decimals = maxOf(0, -floor(log10(step)).toInt())
    return String.format("%.${decimals}f", value)
}

private const val LABEL_EVERY = 2
private const val OFFSCREEN_SLACK = 2.0
