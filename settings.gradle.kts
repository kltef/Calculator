pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cas-calculator"

// The math core is a plain JVM module so it builds and tests anywhere,
// with or without an Android SDK installed.
include(":engine")

// The Android app module needs the Android SDK. Including it on a machine
// without one makes every Gradle invocation fail during configuration, so it
// is only wired in when an SDK is actually available.
val androidSdkPresent =
    file("local.properties").takeIf { it.exists() }
        ?.let { props -> props.readLines().any { it.trimStart().startsWith("sdk.dir=") } } == true ||
        System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null

if (androidSdkPresent) {
    include(":app")
} else {
    logger.lifecycle("No Android SDK detected - skipping :app. Building :engine only.")
}
