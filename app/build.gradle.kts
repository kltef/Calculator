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
    }

    buildTypes {
        release {
            // Minification is OFF pending diagnosis of a startup failure inside
            // Symja's initialisation. Symja drags in kryo, reflectasm, janino,
            // log4j and choco-solver, none of which have keep rules here, and
            // R8 is the one difference between the APK and the engine tests
            // (which pass). Re-enable once the cause is known and the keep
            // rules cover it; the shrunk build is 8 MB against 25 MB.
            isMinifyEnabled = false
            isShrinkResources = false
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

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
