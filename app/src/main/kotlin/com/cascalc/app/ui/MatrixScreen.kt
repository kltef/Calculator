package com.cascalc.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cascalc.app.MatrixUiState
import com.cascalc.engine.Action

/** V5: a grid editor for matrices, with the linear-algebra operations. */
@Composable
fun MatrixScreen(
    state: MatrixUiState,
    onCellChanged: (Int, Int, String) -> Unit,
    onResize: (Int, Int) -> Unit,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val matrixActions = listOf(
        Action.DETERMINANT, Action.INVERSE, Action.EIGENVALUES,
        Action.ROW_REDUCE, Action.TRANSPOSE, Action.RANK,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size: ${state.rows} × ${state.columns}", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onResize(state.rows + 1, state.columns) }) { Text("+row") }
            TextButton(onClick = { onResize(state.rows - 1, state.columns) }) { Text("−row") }
            TextButton(onClick = { onResize(state.rows, state.columns + 1) }) { Text("+col") }
            TextButton(onClick = { onResize(state.rows, state.columns - 1) }) { Text("−col") }
        }

        Column(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.cells.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEachIndexed { columnIndex, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { onCellChanged(rowIndex, columnIndex, it) },
                            modifier = Modifier.width(72.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                textAlign = TextAlign.Center,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(matrixActions.size) { index ->
                val action = matrixActions[index]
                AssistChip(
                    onClick = { onAction(action) },
                    label = { Text(action.label) },
                )
            }
        }

        state.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        state.result?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = result,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                    state.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
