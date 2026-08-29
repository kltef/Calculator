package com.cascalc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cascalc.app.R

/**
 * A key on the pad.
 *
 * @param label what the user sees
 * @param insert what gets typed into the expression; defaults to the label, and
 *               differs for keys like `√(` whose label is shorter than their text
 */
private data class Key(
    val label: String,
    val insert: String = label,
)

private val ScientificKeys = listOf(
    Key("sin", "sin("), Key("cos", "cos("), Key("tan", "tan("),
    Key("asin", "asin("), Key("acos", "acos("), Key("atan", "atan("),
    Key("ln", "ln("), Key("log", "log("), Key("e^", "exp("),
    Key("√", "sqrt("), Key("x²", "^2"), Key("xʸ", "^"),
    Key("π", "pi"), Key("e", "e"), Key("!", "!"),
    Key("abs", "abs("), Key("(", "("), Key(")", ")"),
)

/** Main grid, row by row. */
private val NumericRows = listOf(
    listOf(Key("7"), Key("8"), Key("9"), Key("÷", "÷")),
    listOf(Key("4"), Key("5"), Key("6"), Key("×", "×")),
    listOf(Key("1"), Key("2"), Key("3"), Key("−", "-")),
    listOf(Key("0"), Key("."), Key("%"), Key("+", "+")),
)

@Composable
fun Keypad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onEvaluate: () -> Unit,
    evaluateEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ScientificKeys) { key ->
                OutlinedButton(
                    onClick = { onKey(key.insert) },
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(key.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val clearLabel = stringResource(R.string.clear)
            val backspaceLabel = stringResource(R.string.backspace)
            FilledTonalButton(
                onClick = onClear,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = clearLabel },
            ) {
                Text("AC")
            }
            FilledTonalButton(
                onClick = onBackspace,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = backspaceLabel },
            ) {
                Text("⌫")
            }
            FilledTonalButton(onClick = { onKey("(") }, modifier = Modifier.weight(1f)) {
                Text("(")
            }
            FilledTonalButton(onClick = { onKey(")") }, modifier = Modifier.weight(1f)) {
                Text(")")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier.weight(3f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NumericRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { key ->
                            KeyButton(
                                key = key,
                                onKey = onKey,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            val equalsLabel = stringResource(R.string.equals)
            Button(
                onClick = onEvaluate,
                enabled = evaluateEnabled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = equalsLabel },
            ) {
                Text("=", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun KeyButton(
    key: Key,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = { onKey(key.insert) },
        modifier = modifier.padding(0.dp),
    ) {
        Text(key.label, style = MaterialTheme.typography.titleMedium)
    }
}
