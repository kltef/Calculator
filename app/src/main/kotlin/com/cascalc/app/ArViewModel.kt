package com.cascalc.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cascalc.engine.Action
import com.cascalc.engine.AngleMode
import com.cascalc.engine.CalcResult
import com.cascalc.engine.SolutionStep
import com.cascalc.engine.ar.DetectedText
import com.cascalc.engine.ar.EquationTextCleaner
import com.cascalc.engine.ar.EquationTracker
import com.cascalc.engine.ar.TrackedEquation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArUiState(
    val equations: List<TrackedEquation> = emptyList(),
    val expandedId: Long? = null,
    val steps: List<SolutionStep> = emptyList(),
    val scanning: Boolean = true,
)

/**
 * V8: solves equations seen through the camera.
 *
 * Symja stays the only solver — this adds recognition and rendering, nothing
 * mathematical. Each newly-seen equation is solved once and the answer is
 * pinned to its track, so the overlay does not re-solve the same writing every
 * frame.
 */
class ArViewModel(application: Application) : AndroidViewModel(application) {

    private val session = (application as CasCalculatorApp).session
    private val tracker = EquationTracker()

    private val _uiState = MutableStateFlow(ArUiState())
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()

    /** Ids already sent to the engine, so each equation is solved once. */
    private val solving = mutableSetOf<Long>()

    fun onFrame(detections: List<DetectedText>) {
        if (!_uiState.value.scanning) return

        val usable = detections.mapNotNull { detection ->
            EquationTextCleaner.clean(detection.text)?.let { DetectedText(it, detection.box) }
        }
        val tracked = tracker.update(usable)
        _uiState.value = _uiState.value.copy(equations = tracked)

        tracked.filter { it.solution == null && it.id !in solving }.forEach(::solve)
    }

    private fun solve(equation: TrackedEquation) {
        solving += equation.id
        viewModelScope.launch {
            // An equation with an unknown is solved; anything else is evaluated.
            val action = if (equation.text.contains('=')) Action.SOLVE else Action.EVALUATE
            val result = session.preview(equation.text, AngleMode.RADIANS, action)
            val answer = when (result) {
                is CalcResult.Success -> result.exact
                // A failure is left blank rather than shown: writing that is not
                // really an equation should produce no overlay, not an error
                // floating over the page.
                else -> null
            }
            tracker.attachSolution(equation.id, answer)
            _uiState.value = _uiState.value.copy(equations = tracker.active)
        }
    }

    /** Tap-to-expand: fetch the working for one equation. */
    fun expand(id: Long) {
        val equation = tracker.active.firstOrNull { it.id == id } ?: return
        if (_uiState.value.expandedId == id) {
            _uiState.value = _uiState.value.copy(expandedId = null, steps = emptyList())
            return
        }
        viewModelScope.launch {
            val action = if (equation.text.contains('=')) Action.SOLVE else Action.EVALUATE
            val result = session.preview(equation.text, AngleMode.RADIANS, action)
            _uiState.value = _uiState.value.copy(
                expandedId = id,
                steps = (result as? CalcResult.Success)?.steps.orEmpty(),
            )
        }
    }

    /** Freezes the overlay so the page can be moved without losing the answer. */
    fun toggleScanning() {
        _uiState.value = _uiState.value.copy(scanning = !_uiState.value.scanning)
    }

    fun reset() {
        tracker.reset()
        solving.clear()
        _uiState.value = ArUiState()
    }
}
