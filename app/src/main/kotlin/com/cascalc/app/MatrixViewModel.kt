package com.cascalc.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cascalc.engine.Action
import com.cascalc.engine.AngleMode
import com.cascalc.engine.CalcResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MatrixUiState(
    val cells: List<List<String>> = listOf(
        listOf("1", "2"),
        listOf("3", "4"),
    ),
    val result: String? = null,
    val note: String? = null,
    val error: String? = null,
) {
    val rows: Int get() = cells.size
    val columns: Int get() = cells.firstOrNull()?.size ?: 0

    /** The matrix in calculator syntax, e.g. `{{1,2},{3,4}}`. */
    fun toExpression(): String =
        cells.joinToString(",", prefix = "{", postfix = "}") { row ->
            row.joinToString(",", prefix = "{", postfix = "}") { it.ifBlank { "0" } }
        }
}

class MatrixViewModel(application: Application) : AndroidViewModel(application) {

    private val session = (application as CasCalculatorApp).session

    private val _uiState = MutableStateFlow(MatrixUiState())
    val uiState: StateFlow<MatrixUiState> = _uiState.asStateFlow()

    fun setCell(row: Int, column: Int, value: String) {
        val cells = _uiState.value.cells.mapIndexed { r, existingRow ->
            if (r != row) existingRow
            else existingRow.mapIndexed { c, existing -> if (c == column) value else existing }
        }
        _uiState.value = _uiState.value.copy(cells = cells, result = null, error = null)
    }

    fun resize(rows: Int, columns: Int) {
        if (rows !in 1..MAX_SIZE || columns !in 1..MAX_SIZE) return
        val existing = _uiState.value.cells
        // Keep whatever the user already typed when growing or shrinking.
        val cells = List(rows) { r ->
            List(columns) { c -> existing.getOrNull(r)?.getOrNull(c) ?: "0" }
        }
        _uiState.value = _uiState.value.copy(cells = cells, result = null, error = null)
    }

    fun run(action: Action) {
        val expression = _uiState.value.toExpression()
        viewModelScope.launch {
            when (val result = session.submit(expression, AngleMode.RADIANS, action)) {
                is CalcResult.Success -> _uiState.value = _uiState.value.copy(
                    result = result.exact,
                    note = result.note,
                    error = null,
                )
                is CalcResult.Failure -> _uiState.value = _uiState.value.copy(
                    result = null,
                    error = result.message,
                )
                CalcResult.Empty -> Unit
            }
        }
    }

    private companion object {
        /** Beyond this the grid stops being usable on a phone. */
        const val MAX_SIZE = 6
    }
}
