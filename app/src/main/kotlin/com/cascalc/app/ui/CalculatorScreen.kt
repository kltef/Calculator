package com.cascalc.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.cascalc.app.CalculatorUiState
import com.cascalc.app.R
import com.cascalc.app.Selection
import com.cascalc.engine.AngleMode
import com.cascalc.engine.HistoryEntry

@Composable
fun CalculatorScreen(
    state: CalculatorUiState,
    error: String?,
    onExpressionChanged: (String, Selection) -> Unit,
    onSelectionChanged: (Selection) -> Unit,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onEvaluate: () -> Unit,
    onToggleAngleMode: () -> Unit,
    onToggleHistory: () -> Unit,
    onRecallInput: (HistoryEntry) -> Unit,
    onRecallResult: (HistoryEntry) -> Unit,
    onDeleteHistoryEntry: (Long) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TopBar(
            angleMode = state.angleMode,
            historyVisible = state.historyVisible,
            onToggleAngleMode = onToggleAngleMode,
            onToggleHistory = onToggleHistory,
        )

        AnimatedVisibility(visible = state.historyVisible) {
            HistoryPanel(
                entries = state.history,
                onRecallInput = onRecallInput,
                onRecallResult = onRecallResult,
                onDelete = onDeleteHistoryEntry,
                onClearAll = onClearHistory,
                modifier = Modifier.heightIn(max = 280.dp),
            )
        }

        ExpressionField(
            expression = state.expression,
            selection = state.selection,
            onExpressionChanged = onExpressionChanged,
            onSelectionChanged = onSelectionChanged,
        )

        ResultLine(state = state, error = error)

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
            Keypad(
                onKey = onKey,
                onBackspace = onBackspace,
                onClear = onClear,
                onEvaluate = onEvaluate,
                evaluateEnabled = state.canEvaluate,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun TopBar(
    angleMode: AngleMode,
    historyVisible: Boolean,
    onToggleAngleMode: () -> Unit,
    onToggleHistory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilterChip(
            selected = angleMode == AngleMode.DEGREES,
            onClick = onToggleAngleMode,
            label = { Text(if (angleMode == AngleMode.DEGREES) "DEG" else "RAD") },
        )
        IconButton(onClick = onToggleHistory) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = stringResource(
                    if (historyVisible) R.string.hide_history else R.string.show_history,
                ),
            )
        }
    }
}

@Composable
private fun ExpressionField(
    expression: String,
    selection: Selection,
    onExpressionChanged: (String, Selection) -> Unit,
    onSelectionChanged: (Selection) -> Unit,
) {
    // The ViewModel owns both the text and the caret, so that keypad insertions
    // land where the caret is rather than always at the end.
    val value = remember(expression, selection) {
        TextFieldValue(
            text = expression,
            selection = TextRange(
                selection.start.coerceIn(0, expression.length),
                selection.end.coerceIn(0, expression.length),
            ),
        )
    }

    OutlinedTextField(
        value = value,
        onValueChange = { updated ->
            val newSelection = Selection(updated.selection.start, updated.selection.end)
            if (updated.text == expression) {
                onSelectionChanged(newSelection)
            } else {
                onExpressionChanged(updated.text, newSelection)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.End),
        placeholder = { Text(stringResource(R.string.expression_hint)) },
        singleLine = false,
        maxLines = 3,
        // The on-screen keypad is the primary input, but a physical or system
        // keyboard should still produce something sensible.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
}

@Composable
private fun ResultLine(state: CalculatorUiState, error: String?) {
    val preview = state.previewText
    val approximation = state.previewApproximation
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        horizontalAlignment = Alignment.End,
    ) {
        when {
            error != null -> Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )

            preview != null -> {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.headlineMedium,
                )
                approximation?.let { approximate ->
                    Text(
                        text = "${stringResource(R.string.approx_prefix)} $approximate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
