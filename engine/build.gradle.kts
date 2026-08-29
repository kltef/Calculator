import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Targets JVM 17 bytecode (what the Android app module consumes) while
// compiling with whatever JDK 17+ happens to be installed.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.symja.core)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "failed", "skipped") }
}
