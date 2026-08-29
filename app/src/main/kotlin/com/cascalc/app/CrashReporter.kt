package com.cascalc.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Records an uncaught exception so it can be shown on the next launch.
 *
 * A crash on startup gives the user nothing but the system's "app keeps
 * stopping" dialog, and gives the developer nothing at all unless the device is
 * plugged into a debugger. Persisting the stack trace and surfacing it in-app
 * turns an unreportable crash into something a user can read out or copy.
 */
class CrashReporter(private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    /** Chains onto the existing handler so the system still does its part. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { file.writeText(format(thread, throwable)) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The last recorded crash, if there is one. */
    fun lastCrash(): String? = runCatching {
        file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    private fun format(thread: Thread, throwable: Throwable): String {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
        return buildString {
            appendLine("Thread: ${thread.name}")
            appendLine("Android: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.MODEL})")
            appendLine()
            append(stack.toString())
        }
    }

    private companion object {
        const val FILE_NAME = "last-crash.txt"
    }
}
