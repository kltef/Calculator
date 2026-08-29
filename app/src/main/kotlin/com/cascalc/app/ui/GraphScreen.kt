package com.cascalc.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cascalc.app.GraphUiState

/**
 * V3: plot f(x) with pan, zoom, trace and root/intersection finding.
 *
 * Up to four functions share one window. Each gets a colour that matches its
 * input row, so the legend is the input itself rather than a separate key.
 */
@Composable
fun GraphScreen(
    state: GraphUiState,
    onExpressionChanged: (Int, String) -> Unit,
    onAddFunction: () -> Unit,
    onRemoveFunction: (Int) -> Unit,
    onToggleVisible: (Int) -> Unit,
    onTransform: (Double, Double, Double, Double, Double) -> Unit,
    onTrace: (Double) -> Unit,
    onToggleTrace: () -> Unit,
    onFindRoots: () -> Unit,
    onFindIntersections: () -> Unit,
    onResetWindow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {

        state.functions.forEachIndexed { index, function ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            CurveColors[index % CurveColors.size].copy(
                                alpha = if (function.visible) 1f else 0.3f,
                            ),
                            CircleShape,
                        ),
                )
                OutlinedTextField(
                    value = function.expression,
                    onValueChange = { onExpressionChanged(function.id, it) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    placeholder = { Text("f(x) = …") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                IconButton(onClick = { onToggleVisible(function.id) }) {
                    Text(if (function.visible) "👁" else "◌")
                }
                if (state.functions.size > 1) {
                    IconButton(onClick = { onRemoveFunction(function.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove function")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onAddFunction) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add")
            }
            FilterChip(
                selected = state.traceEnabled,
                onClick = onToggleTrace,
                label = { Text("Trace") },
            )
            AssistChip(onClick = onFindRoots, label = { Text("Roots") })
            AssistChip(onClick = onFindIntersections, label = { Text("Intersect") })
            AssistChip(onClick = onResetWindow, label = { Text("Reset") })
        }

        if (state.computing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }

        GraphCanvas(
            window = state.window,
            curves = state.curves,
            markers = state.markers,
            traceEnabled = state.traceEnabled,
            onTransform = onTransform,
            onTrace = onTrace,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
        )

        if (state.markers.isNotEmpty()) {
            Text(
                text = state.markers.joinToString("   ") { it.label },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
