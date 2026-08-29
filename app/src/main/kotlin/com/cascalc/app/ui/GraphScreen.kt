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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cascalc.app.GraphMode
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
    onSetMode: (GraphMode) -> Unit,
    onToggleDerivative: () -> Unit,
    onShadeArea: () -> Unit,
    onTangentAt: (Double) -> Unit,
    onClearOverlays: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Curves sweep in when the set of plotted expressions changes, but not on
    // every pan — re-animating during a drag would look like stutter.
    val signature = state.curves.joinToString { it.expression } + state.mode
    var reveal by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(signature) {
        reveal = 0f
        reveal = 1f
    }
    val animatedReveal by animateFloatAsState(
        targetValue = reveal,
        animationSpec = tween(durationMillis = 520),
        label = "curveReveal",
    )
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

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(GraphMode.entries.size) { index ->
                val mode = GraphMode.entries[index]
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { onSetMode(mode) },
                    label = { Text(mode.label) },
                )
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

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.showDerivative,
                    onClick = onToggleDerivative,
                    label = { Text("f′") },
                )
            }
            item { AssistChip(onClick = onShadeArea, label = { Text("Area") }) }
            item {
                AssistChip(
                    onClick = { onTangentAt(state.window.xMin + state.window.width / 2) },
                    label = { Text("Tangent") },
                )
            }
            item { AssistChip(onClick = onClearOverlays, label = { Text("Clear") }) }
        }

        AnimatedVisibility(
            visible = state.computing,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(240)),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
            Text(
                text = state.error.orEmpty(),
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
            tangent = state.tangent,
            area = state.area,
            reveal = animatedReveal,
            onTransform = onTransform,
            onTrace = onTrace,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
        )

        val caption = listOfNotNull(
            state.areaLabel,
            state.markers.takeIf { it.isNotEmpty() }?.joinToString("   ") { it.label },
        ).joinToString("   ")
        AnimatedVisibility(
            visible = caption.isNotBlank(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
