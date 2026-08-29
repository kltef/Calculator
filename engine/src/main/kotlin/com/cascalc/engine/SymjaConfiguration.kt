package com.cascalc.engine

import org.matheclipse.core.basic.Config

/**
 * Configures Symja for an environment without a desktop JVM.
 *
 * Symja is built for the desktop, and parts of its builtin catalogue reference
 * classes Android does not ship — `Export` pulls in `java.awt.image.RenderedImage`,
 * the memory builtins want `java.lang.management`. Those references are resolved
 * when the owning class is verified, which happens while `F`'s static
 * initialiser registers the catalogue. The result is a `NoClassDefFoundError`
 * from deep inside library initialisation, before any user code runs.
 *
 * [apply] must run *before* anything touches [org.matheclipse.core.expression.F],
 * because that is the point of no return: once `F` has initialised, these flags
 * have no effect.
 *
 * `FUZZY_PARSER` is badly named — it does not change the parser. Its only uses
 * in the library are to gate registration of builtins that need a filesystem,
 * Java reflection, a compiler, or image export. Skipping those is exactly what
 * a calculator wants, and it is what makes the catalogue safe to initialise
 * here. What it costs is documented in the README.
 */
internal object SymjaConfiguration {

    private var applied = false

    @Synchronized
    fun apply() {
        if (applied) return
        applied = true

        Config.FUZZY_PARSER = true
        Config.JAVA_AWT_DESKTOP_AVAILABLE = false
        Config.DISABLE_JMX = true
        Config.SWING_PLOT_FRAME = false
        Config.SHOW_STACKTRACE = false

        // Symja wraps long output on its own width heuristic, so the same
        // matrix can come back on one line or several. Formatting belongs to
        // the UI, so wrapping is turned off and ResultFormatter stacks rows.
        Config.MAX_OUTPUT_LINE = Int.MAX_VALUE
    }
}
