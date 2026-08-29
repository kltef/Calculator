package com.cascalc.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.cascalc.app.Appearance
import com.cascalc.app.Density
import com.cascalc.app.ThemeChoice

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5DA8),
    secondary = Color(0xFF4E6591),
    tertiary = Color(0xFF7A5296),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC7FF),
    secondary = Color(0xFFBCC7E5),
    tertiary = Color(0xFFE0BAFF),
)

@Composable
fun CasCalculatorTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appearance.theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

    val context = LocalContext.current
    val useDynamic = appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        useDynamic ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Density scales every dp in the tree, so a compact setting tightens the
    // whole UI rather than only the places that remembered to check.
    val baseDensity = LocalDensity.current
    val scaled = androidx.compose.ui.unit.Density(
        density = baseDensity.density * when (appearance.density) {
            Density.COMPACT -> COMPACT_SCALE
            Density.COMFORTABLE -> 1f
        },
        fontScale = baseDensity.fontScale,
    )

    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

private const val COMPACT_SCALE = 0.88f
