package com.cascalc.app

import android.content.Context

/** How the app should look. V7's theming and density options. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Controls how tightly the UI packs; smaller screens benefit from COMPACT. */
enum class Density { COMPACT, COMFORTABLE }

data class Appearance(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val density: Density = Density.COMFORTABLE,
    val dynamicColor: Boolean = true,
)

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("cas_calculator_settings", Context.MODE_PRIVATE)

    fun load(): Appearance = Appearance(
        theme = read(KEY_THEME, ThemeChoice.entries, ThemeChoice.SYSTEM),
        density = read(KEY_DENSITY, Density.entries, Density.COMFORTABLE),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
    )

    fun save(appearance: Appearance) {
        prefs.edit()
            .putString(KEY_THEME, appearance.theme.name)
            .putString(KEY_DENSITY, appearance.density.name)
            .putBoolean(KEY_DYNAMIC, appearance.dynamicColor)
            .apply()
    }

    private fun <T : Enum<T>> read(key: String, values: List<T>, fallback: T): T {
        val stored = prefs.getString(key, null) ?: return fallback
        return values.firstOrNull { it.name == stored } ?: fallback
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DENSITY = "density"
        const val KEY_DYNAMIC = "dynamic_color"
    }
}
