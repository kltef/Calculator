package com.cascalc.app

import android.app.Application
import com.cascalc.engine.CalculatorSession

/**
 * Owns the single math engine for the whole process.
 *
 * Every screen shares it, for two reasons: building Symja is expensive enough
 * that a second copy is worth avoiding, and variables the user defines on the
 * calculator screen should be usable when graphing or working with matrices.
 * Its lifetime is the process, so it is never closed.
 */
class CasCalculatorApp : Application() {

    lateinit var crashReporter: CrashReporter
        private set

    val session: CalculatorSession by lazy { CalculatorSession() }

    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        crashReporter = CrashReporter(this).apply { install() }
        settings = SettingsStore(this)
    }
}
