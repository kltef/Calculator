package com.cascalc.app

import android.app.Application

class CasCalculatorApp : Application() {

    lateinit var crashReporter: CrashReporter
        private set

    override fun onCreate() {
        super.onCreate()
        crashReporter = CrashReporter(this).apply { install() }
    }
}
