package com.cascalc.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cascalc.engine.BaseConverter
import com.cascalc.engine.Constants
import com.cascalc.engine.Units

/** V6's catch-all extras: unit conversion, constants and base conversion. */
@Composable
fun ToolsScreen(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Units", "Constants", "Bases")

    Column(modifier = modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> UnitConverter(modifier = Modifier.padding(12.dp))
            1 -> ConstantsList(onInsert = onInsert)
            else -> BaseConverterPanel(modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun UnitConverter(modifier: Modifier = Modifier) {
    var amount by remember { mutableStateOf("1") }
    var dimension by remember { mutableStateOf(Units.dimensions.first()) }
    var from by remember { mutableStateOf(Units.unitsIn(dimension).first().name) }
    var to by remember {
        mutableStateOf(Units.unitsIn(dimension).getOrElse(1) { Units.unitsIn(dimension).first() }.name)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UnitPicker("Measuring", dimension, Units.dimensions) { picked ->
            dimension = picked
            val units = Units.unitsIn(picked)
            from = units.first().name
            to = units.getOrElse(1) { units.first() }.name
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        val unitNames = Units.unitsIn(dimension).map { it.name }
        UnitPicker("From", from, unitNames) { from = it }
        UnitPicker("To", to, unitNames) { to = it }

        val value = amount.toDoubleOrNull()
        val text = when {
            value == null -> "Enter a number"
            else -> when (val result = Units.convert(value, from, to)) {
                is Units.Result.Converted -> result.text
                is Units.Result.Mismatched ->
                    "${result.from.name} and ${result.to.name} measure different things"
                is Units.Result.Unknown -> "Unknown unit: ${result.name}"
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun UnitPicker(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label:", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { expanded = true }) { Text(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ConstantsList(onInsert: (String) -> Unit) {
    LazyColumn {
        items(Constants.ALL) { constant ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInsert(constant.expression) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "${constant.symbol}   ${constant.name}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = buildString {
                        append(constant.unit.ifBlank { "dimensionless" })
                        if (constant.exact) append("  ·  exact by definition")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun BaseConverterPanel(modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("255") }
    var fromBase by remember { mutableIntStateOf(10) }
    val bases = listOf(2, 8, 10, 16)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Value") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UnitPicker("Input base", fromBase.toString(), bases.map { it.toString() }) {
            fromBase = it.toInt()
        }

        val decimal = BaseConverter.parse(input, fromBase)
        if (decimal !is BaseConverter.Result.Converted) {
            Text(
                text = "Not a valid base-$fromBase number",
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }
        bases.forEach { base ->
            val formatted = BaseConverter.formatDecimal(decimal.text, base)
            if (formatted is BaseConverter.Result.Converted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("base $base", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = BaseConverter.withPrefix(formatted.text, base),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
