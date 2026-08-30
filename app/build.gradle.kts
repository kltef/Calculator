plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cascalc.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cascalc.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            // MediaPipe ships native code for four ABIs. Every phone that can
            // run this is arm; including x86 would roughly double the native
            // payload to serve emulators only.
            // arm64 only. MediaPipe's 32-bit library costs another ~3 MB, and
            // a device without arm64 is now reported explicitly in the AR
            // status line rather than looking like a bug.
            abiFilters += setOf("arm64-v8a")
        }
    }

    androidResources {
        // The hand-landmark model must stay uncompressed: MediaPipe memory-maps
        // it straight out of the APK, which a deflated asset makes impossible.
        noCompress += "task"
    }

    buildTypes {
        release {
            // Minification is back on: MediaPipe's native library and model
            // put the unminified build far over any reasonable download size.
            // The earlier startup crash is addressed in proguard-rules.pro,
            // which now keeps Symja's reflective dependency graph and turns
            // obfuscation off entirely - renaming, not shrinking, is what
            // breaks name-based reflection.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Symja leans on java.time and other desugarable APIs.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // Symja ships several duplicated metadata files across its dependencies.
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "META-INF/versions/**",
            )
            // matheclipse-external shades jgrapht, so the same schema files
            // arrive twice. They are only used by GraphML import/export, which
            // the calculator never touches; either copy is fine.
            pickFirsts += setOf(
                "**/*.xsd",
                "**/*.properties",
            )
        }
    }

    sourceSets["main"].java.srcDir("src/main/kotlin")
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // V8 AR: camera capture and on-device text recognition. ML Kit here is the
    // Play-services variant, which fetches its model on demand rather than
    // bundling ~16 MB of it into the APK.
    //
    // ARCore is deliberately NOT a dependency - see ArScreen and ROADMAP.md.
    // World anchoring needs ARCore to own the camera and render the feed
    // through OpenGL, which rules out CameraX preview and ML Kit analysis on
    // the same stream. Tracking here is screen-space with motion smoothing.
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.mlkit.vision)
    implementation(libs.mlkit.text.recognition)
    // Hand landmarks for point-to-select in AR mode.
    implementation(libs.mediapipe.tasks.vision)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
