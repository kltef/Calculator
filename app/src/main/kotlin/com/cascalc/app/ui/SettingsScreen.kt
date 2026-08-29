package com.cascalc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cascalc.app.Appearance
import com.cascalc.app.Density
import com.cascalc.app.ThemeChoice

/** V7's appearance settings: theme, dynamic colour and UI density. */
@Composable
fun SettingsScreen(
    appearance: Appearance,
    onChange: (Appearance) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Theme", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ThemeChoice.entries.size) { index ->
                val choice = ThemeChoice.entries[index]
                FilterChip(
                    selected = appearance.theme == choice,
                    onClick = { onChange(appearance.copy(theme = choice)) },
                    label = { Text(choice.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Match system colours", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Uses the wallpaper palette on Android 12 and newer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = appearance.dynamicColor,
                onCheckedChange = { onChange(appearance.copy(dynamicColor = it)) },
            )
        }

        HorizontalDivider()

        Text("Density", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Density.entries.size) { index ->
                val density = Density.entries[index]
                FilterChip(
                    selected = appearance.density == density,
                    onClick = { onChange(appearance.copy(density = density)) },
                    label = { Text(density.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }
}
