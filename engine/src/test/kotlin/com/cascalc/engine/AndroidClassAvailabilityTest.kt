package com.cascalc.engine

import java.net.URL
import java.net.URLClassLoader
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reproduces Android's class availability on the JVM.
 *
 * The desktop JVM has `java.awt`, `javax.imageio`, `sun.misc.Unsafe` and friends;
 * Android does not. Code that merely *references* them is fine — the reference
 * is only resolved when the owning class initialises — so a build that compiles
 * and shrinks cleanly can still die with `NoClassDefFoundError` the moment it
 * touches the wrong part of a library.
 *
 * This loader hides exactly those packages, so a test that runs the engine
 * inside it fails here in the same way it would fail on a device.
 */
private class AndroidLikeClassLoader(urls: Array<URL>) :
    URLClassLoader(urls, getPlatformClassLoader()) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (ABSENT_ON_ANDROID.any { name == it || name.startsWith("$it.") }) {
            throw ClassNotFoundException("$name is not available on Android")
        }
        return super.loadClass(name, resolve)
    }

    companion object {
        val ABSENT_ON_ANDROID = listOf(
            "java.awt",
            "javax.imageio",
            "javax.swing",
            "javax.lang.model",
            "java.lang.management",
            "org.osgi",
            "sun.misc",
        )
    }
}

class AndroidClassAvailabilityTest {

    /**
     * Runs [body] against a `CasEngine` loaded without the classes Android lacks.
     * Returns the engine's answer, or rethrows whatever a device would have hit.
     */
    private fun inAndroidLikeLoader(input: String): String {
        val classpath = System.getProperty("java.class.path")
            .split(java.io.File.pathSeparator)
            .map { java.io.File(it).toURI().toURL() }
            .toTypedArray()

        AndroidLikeClassLoader(classpath).use { loader ->
            val previous = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = loader
            try {
                val engineClass = loader.loadClass("com.cascalc.engine.CasEngine")
                val angleModeClass = loader.loadClass("com.cascalc.engine.AngleMode")
                val actionClass = loader.loadClass("com.cascalc.engine.Action")
                val radians = angleModeClass.getField("RADIANS").get(null)
                val evaluateAction = actionClass.getField("EVALUATE").get(null)

                val engine = engineClass.getConstructor(Long::class.java).newInstance(5_000L)
                val evaluate = engineClass.getMethod(
                    "evaluate",
                    String::class.java,
                    angleModeClass,
                    actionClass,
                )
                val result = evaluate.invoke(engine, input, radians, evaluateAction)
                return result.javaClass.getMethod("getExact").invoke(result) as String
            } finally {
                Thread.currentThread().contextClassLoader = previous
            }
        }
    }

    @Test fun `engine starts and evaluates without the classes Android lacks`() {
        assertEquals("1/2", inAndroidLikeLoader("1/3 + 1/6"))
    }

    @Test fun `symbolic work does not reach desktop-only classes`() {
        assertEquals("1+2·x+x^2", inAndroidLikeLoader("Expand((x+1)^2)"))
    }
}
