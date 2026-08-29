package com.cascalc.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cascalc.engine.AngleMode
import com.cascalc.engine.PlotCurve
import com.cascalc.engine.PlotPoint
import com.cascalc.engine.PlotWindow
import com.cascalc.engine.Plotter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One plotted function, as the user entered it. */
data class GraphFunction(
    val id: Int,
    val expression: String,
    val visible: Boolean = true,
)

/** A point the user is inspecting, from trace or a found root/intersection. */
data class GraphMarker(
    val point: PlotPoint,
    val label: String,
)

data class GraphUiState(
    val functions: List<GraphFunction> = listOf(GraphFunction(1, "")),
    val window: PlotWindow = PlotWindow.DEFAULT,
    val curves: List<PlotCurve> = emptyList(),
    val markers: List<GraphMarker> = emptyList(),
    val traceEnabled: Boolean = false,
    val error: String? = null,
    val computing: Boolean = false,
)

class GraphViewModel(application: Application) : AndroidViewModel(application) {

    private val session = (application as CasCalculatorApp).session

    private val _uiState = MutableStateFlow(GraphUiState())
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    private var plotJob: Job? = null
    private var angleMode: AngleMode = AngleMode.RADIANS

    fun setAngleMode(mode: AngleMode) {
        if (angleMode == mode) return
        angleMode = mode
        replot()
    }

    fun setExpression(id: Int, expression: String) {
        _uiState.value = _uiState.value.copy(
            functions = _uiState.value.functions.map {
                if (it.id == id) it.copy(expression = expression) else it
            },
        )
        replot()
    }

    fun addFunction() {
        val functions = _uiState.value.functions
        if (functions.size >= MAX_FUNCTIONS) return
        val nextId = (functions.maxOfOrNull { it.id } ?: 0) + 1
        _uiState.value = _uiState.value.copy(functions = functions + GraphFunction(nextId, ""))
    }

    fun removeFunction(id: Int) {
        val remaining = _uiState.value.functions.filterNot { it.id == id }
        _uiState.value = _uiState.value.copy(
            functions = remaining.ifEmpty { listOf(GraphFunction(1, "")) },
        )
        replot()
    }

    fun toggleVisible(id: Int) {
        _uiState.value = _uiState.value.copy(
            functions = _uiState.value.functions.map {
                if (it.id == id) it.copy(visible = !it.visible) else it
            },
        )
        replot()
    }

    fun toggleTrace() {
        _uiState.value = _uiState.value.copy(
            traceEnabled = !_uiState.value.traceEnabled,
            markers = emptyList(),
        )
    }

    /** Pan and zoom, in graph units. Zoom is about [focusX], [focusY]. */
    fun transform(panX: Double, panY: Double, zoom: Double, focusX: Double, focusY: Double) {
        val window = _uiState.value.window
        val scale = (1.0 / zoom).coerceIn(MIN_ZOOM_STEP, MAX_ZOOM_STEP)

        val newWidth = (window.width * scale).coerceIn(MIN_SPAN, MAX_SPAN)
        val newHeight = (window.height * scale).coerceIn(MIN_SPAN, MAX_SPAN)

        // Keep the focus point under the fingers while scaling around it.
        val fx = (focusX - window.xMin) / window.width
        val fy = (focusY - window.yMin) / window.height

        val xMin = focusX - fx * newWidth + panX
        val yMin = focusY - fy * newHeight + panY

        val updated = PlotWindow(xMin, xMin + newWidth, yMin, yMin + newHeight)
        if (!updated.isValid()) return
        _uiState.value = _uiState.value.copy(window = updated)
        replot()
    }

    fun resetWindow() {
        _uiState.value = _uiState.value.copy(window = PlotWindow.DEFAULT, markers = emptyList())
        replot()
    }

    /** Trace: snap to the nearest point on the first visible curve. */
    fun traceAt(x: Double) {
        val state = _uiState.value
        val curve = state.curves.firstOrNull() ?: return
        val nearest = curve.segments.flatten().minByOrNull { kotlin.math.abs(it.x - x) } ?: return
        _uiState.value = state.copy(
            markers = listOf(
                GraphMarker(
                    nearest,
                    "(${format(nearest.x)}, ${format(nearest.y)})",
                ),
            ),
        )
    }

    /** V3's feedback into V2: mark the roots, so they can be solved exactly. */
    fun findRoots() {
        val state = _uiState.value
        val target = state.functions.firstOrNull { it.visible && it.expression.isNotBlank() } ?: return
        viewModelScope.launch {
            val markers = withContext(Dispatchers.Default) {
                val f = compile(target.expression) ?: return@withContext emptyList()
                Plotter(f).roots(state.window).map { x ->
                    GraphMarker(PlotPoint(x, 0.0), "root at x = ${format(x)}")
                }
            }
            _uiState.value = _uiState.value.copy(
                markers = markers,
                error = if (markers.isEmpty()) "No roots visible in this window" else null,
            )
        }
    }

    fun findIntersections() {
        val state = _uiState.value
        val visible = state.functions.filter { it.visible && it.expression.isNotBlank() }
        if (visible.size < 2) {
            _uiState.value = state.copy(error = "Add a second function to find intersections")
            return
        }
        viewModelScope.launch {
            val markers = withContext(Dispatchers.Default) {
                val first = compile(visible[0].expression) ?: return@withContext emptyList()
                val second = compile(visible[1].expression) ?: return@withContext emptyList()
                Plotter.intersections(first, second, state.window).map { point ->
                    GraphMarker(point, "(${format(point.x)}, ${format(point.y)})")
                }
            }
            _uiState.value = _uiState.value.copy(
                markers = markers,
                error = if (markers.isEmpty()) "No intersections in this window" else null,
            )
        }
    }

    private fun replot() {
        plotJob?.cancel()
        val state = _uiState.value
        plotJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(computing = true)
            val curves = withContext(Dispatchers.Default) {
                state.functions
                    .filter { it.visible && it.expression.isNotBlank() }
                    .map { function ->
                        val f = compile(function.expression)
                        PlotCurve(
                            expression = function.expression,
                            segments = f?.let { Plotter(it).sample(state.window) } ?: emptyList(),
                        )
                    }
            }
            val unplottable = curves.any { it.isEmpty }
            _uiState.value = _uiState.value.copy(
                curves = curves,
                computing = false,
                error = if (unplottable) "Nothing to draw in this window" else null,
            )
        }
    }

    /**
     * Compiles on the engine thread, then hands back a plain function.
     *
     * The engine is not thread-safe, so the *compilation* is confined; the
     * returned lambda still calls into Symja and therefore must only be used
     * from one thread at a time, which the single sampling coroutine ensures.
     */
    private suspend fun compile(expression: String): ((Double) -> Double)? =
        session.numericFunction(expression, angleMode)

    private fun format(value: Double): String =
        com.cascalc.engine.ResultFormatter.formatDouble(value)

    private companion object {
        const val MAX_FUNCTIONS = 4
        const val MIN_SPAN = 1e-6
        const val MAX_SPAN = 1e9
        const val MIN_ZOOM_STEP = 0.2
        const val MAX_ZOOM_STEP = 5.0
    }
}
