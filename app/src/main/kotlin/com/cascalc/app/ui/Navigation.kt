package com.cascalc.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level destinations. Deliberately a plain enum rather than a nav library:
 *  the app has one flat level of navigation and no deep links to route. */
enum class Screen(val label: String, val icon: ImageVector) {
    CALCULATOR("Calc", Icons.Filled.Calculate),
    GRAPH("Graph", Icons.Filled.ShowChart),
    MATRIX("Matrix", Icons.Filled.Grid4x4),
    TOOLS("Tools", Icons.Filled.Functions),
    PRACTICE("Practice", Icons.Filled.School),
    REFERENCE("Formulas", Icons.Filled.MenuBook),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun BottomBar(
    current: Screen,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        Screen.entries.forEach { screen ->
            NavigationBarItem(
                selected = current == screen,
                onClick = { onSelect(screen) },
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                alwaysShowLabel = false,
            )
        }
    }
}
