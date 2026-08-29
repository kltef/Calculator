package com.cascalc.app

import android.content.Context
import com.cascalc.engine.AngleMode
import com.cascalc.engine.CalcResult
import com.cascalc.engine.HistoryEntry
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Persists calculation history across app restarts.
 *
 * History is a handful of short strings, so SharedPreferences holding a small
 * JSON array is enough; there is no need to pull in a database for V1.
 */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toEntry() }
        } catch (e: JSONException) {
            // Corrupt history is not worth crashing over - start fresh.
            emptyList()
        }
    }

    fun save(entries: List<HistoryEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    fun loadAngleMode(): AngleMode =
        if (prefs.getString(KEY_ANGLE_MODE, null) == AngleMode.DEGREES.name) {
            AngleMode.DEGREES
        } else {
            AngleMode.RADIANS
        }

    fun saveAngleMode(mode: AngleMode) {
        prefs.edit().putString(KEY_ANGLE_MODE, mode.name).apply()
    }

    private fun HistoryEntry.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("input", input)
        put("exact", result.exact)
        put("approx", result.approximate ?: JSONObject.NULL)
        put("raw", result.raw)
        put("angleMode", angleMode.name)
        put("at", timestampMillis)
    }

    private fun JSONObject.toEntry(): HistoryEntry? {
        val input = optString("input").takeIf { it.isNotEmpty() } ?: return null
        val exact = optString("exact").takeIf { it.isNotEmpty() } ?: return null
        return HistoryEntry(
            id = optLong("id"),
            input = input,
            result = CalcResult.Success(
                exact = exact,
                approximate = if (isNull("approx")) null else optString("approx"),
                raw = optString("raw", exact),
            ),
            angleMode = if (optString("angleMode") == AngleMode.DEGREES.name) {
                AngleMode.DEGREES
            } else {
                AngleMode.RADIANS
            },
            timestampMillis = optLong("at"),
        )
    }

    private companion object {
        const val PREFS_NAME = "cas_calculator_history"
        const val KEY_ENTRIES = "entries"
        const val KEY_ANGLE_MODE = "angle_mode"
    }
}
