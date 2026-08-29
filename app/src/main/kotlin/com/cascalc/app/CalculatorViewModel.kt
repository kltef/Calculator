package com.cascalc.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cascalc.engine.Action
import com.cascalc.engine.AngleMode
import com.cascalc.engine.CalcResult
import com.cascalc.engine.CalculatorSession
import com.cascalc.engine.HistoryEntry
import com.cascalc.engine.SolutionStep
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the caret sits, so keypad insertions land in the right place. */
data class Selection(val start: Int, val end: Int)

data class CalculatorUiState(
    val expression: String = "",
    val selection: Selection = Selection(0, 0),
    val preview: CalcResult = CalcResult.Empty,
    val angleMode: AngleMode = AngleMode.RADIANS,
    val history: List<HistoryEntry> = emptyList(),
    val historyVisible: Boolean = false,
    val variables: Map<String, String> = emptyMap(),
    val variablesVisible: Boolean = false,
    /** The result of the last action the user actually committed. */
    val committed: CalcResult.Success? = null,
    val steps: List<SolutionStep> = emptyList(),
    val stepsVisible: Boolean = false,
) {
    /** Errors are hidden while typing — half-typed input is not a mistake yet. */
    val previewText: String? = (preview as? CalcResult.Success)?.exact

    val previewApproximation: String? = (preview as? CalcResult.Success)?.approximate

    val canEvaluate: Boolean = preview is CalcResult.Success
}

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val store = HistoryStore(application)
    private val session = CalculatorSession()

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    /** The error shown after pressing "=", cleared as soon as the user types. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var previewJob: Job? = null

    init {
        session.history.restore(store.load())
        _uiState.value = _uiState.value.copy(
            angleMode = store.loadAngleMode(),
            history = session.history.entries.value,
        )
        viewModelScope.launch {
            session.history.entries.collect { entries ->
                _uiState.value = _uiState.value.copy(history = entries)
                store.save(entries)
            }
        }
        viewModelScope.launch {
            session.variables.collect { variables ->
                _uiState.value = _uiState.value.copy(variables = variables)
            }
        }
    }

    fun onExpressionChanged(text: String, selection: Selection) {
        _error.value = null
        val current = _uiState.value
        _uiState.value = current.copy(
            expression = text,
            selection = selection,
            // Steps belong to the expression that produced them.
            committed = if (text == current.expression) current.committed else null,
            steps = if (text == current.expression) current.steps else emptyList(),
            stepsVisible = if (text == current.expression) current.stepsVisible else false,
        )
        schedulePreview()
    }

    fun onSelectionChanged(selection: Selection) {
        _uiState.value = _uiState.value.copy(selection = selection)
    }

    /** Inserts keypad text at the caret, replacing any selection. */
    fun insert(text: String) {
        val state = _uiState.value
        val start = state.selection.start.coerceIn(0, state.expression.length)
        val end = state.selection.end.coerceIn(start, state.expression.length)
        val updated = state.expression.replaceRange(start, end, text)
        onExpressionChanged(updated, Selection(start + text.length, start + text.length))
    }

    fun backspace() {
        val state = _uiState.value
        val start = state.selection.start.coerceIn(0, state.expression.length)
        val end = state.selection.end.coerceIn(start, state.expression.length)
        if (start == end) {
            if (start == 0) return
            val updated = state.expression.removeRange(start - 1, start)
            onExpressionChanged(updated, Selection(start - 1, start - 1))
        } else {
            val updated = state.expression.removeRange(start, end)
            onExpressionChanged(updated, Selection(start, start))
        }
    }

    fun clear() {
        onExpressionChanged("", Selection(0, 0))
    }

    fun toggleAngleMode() {
        val next = when (_uiState.value.angleMode) {
            AngleMode.RADIANS -> AngleMode.DEGREES
            AngleMode.DEGREES -> AngleMode.RADIANS
        }
        store.saveAngleMode(next)
        _uiState.value = _uiState.value.copy(angleMode = next)
        schedulePreview()
    }

    fun toggleHistory() {
        _uiState.value = _uiState.value.copy(
            historyVisible = !_uiState.value.historyVisible,
            variablesVisible = false,
        )
    }

    fun toggleVariables() {
        _uiState.value = _uiState.value.copy(
            variablesVisible = !_uiState.value.variablesVisible,
            historyVisible = false,
        )
    }

    fun toggleSteps() {
        _uiState.value = _uiState.value.copy(stepsVisible = !_uiState.value.stepsVisible)
    }

    fun deleteVariable(name: String) {
        viewModelScope.launch { session.clearVariable(name) }
    }

    fun evaluate() = run(Action.EVALUATE)

    /**
     * Commits the current expression under [action].
     *
     * What happens to the editor afterwards depends on the action, because the
     * useful next move differs: a plain evaluation is usually the input to
     * another calculation, whereas a solve or a simplify is something you want
     * to keep looking at next to what you typed.
     */
    fun run(action: Action) {
        val state = _uiState.value
        if (state.expression.isBlank()) return
        viewModelScope.launch {
            when (val result = session.submit(state.expression, state.angleMode, action)) {
                is CalcResult.Success -> {
                    _error.value = null
                    _uiState.value = _uiState.value.copy(
                        committed = result,
                        steps = result.steps,
                        stepsVisible = result.steps.isNotEmpty(),
                    )
                    when {
                        // An assignment is done; clear the line for the next one.
                        result.note?.startsWith("Stored") == true -> clear()
                        // A plain value can be carried forward as new input.
                        action == Action.EVALUATE && result.steps.isEmpty() && result.note == null ->
                            onExpressionChanged(
                                result.raw,
                                Selection(result.raw.length, result.raw.length),
                            )
                        // Otherwise leave the expression alone so it stays
                        // visible beside its answer.
                        else -> Unit
                    }
                }
                is CalcResult.Failure -> {
                    _error.value = result.message
                    _uiState.value = _uiState.value.copy(
                        committed = null,
                        steps = emptyList(),
                        stepsVisible = false,
                    )
                }
                CalcResult.Empty -> Unit
            }
        }
    }

    /** Puts a past calculation's input back in the editor. */
    fun recallInput(entry: HistoryEntry) {
        onExpressionChanged(entry.input, Selection(entry.input.length, entry.input.length))
    }

    /** Inserts a past calculation's result at the caret. */
    fun recallResult(entry: HistoryEntry) {
        insert(entry.result.raw)
    }

    fun deleteHistoryEntry(id: Long) = session.history.remove(id)

    fun clearHistory() = session.history.clear()

    /**
     * Debounced so that fast typing doesn't queue an evaluation per keystroke;
     * the engine runs on its own thread and only the latest input matters.
     */
    private fun schedulePreview() {
        previewJob?.cancel()
        val state = _uiState.value
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MILLIS)
            val result = session.preview(state.expression, state.angleMode)
            // Ignore a stale result if the user kept typing.
            if (_uiState.value.expression == state.expression) {
                _uiState.value = _uiState.value.copy(preview = result)
            }
        }
    }

    override fun onCleared() {
        session.close()
        super.onCleared()
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MILLIS = 120L
    }
}
