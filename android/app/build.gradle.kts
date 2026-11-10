plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "pdx.bridgeride.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "pdx.bridgeride.app"
        minSdk = 29
        targetSdk = 36
        // Bump both for every release tag (v0.1.0 -> 1, v0.1.1 -> 2, ...).
        versionCode = 3
        versionName = "0.2.1"
        // Phone (S24) and the CI emulator only, to keep the APK small.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // Pre-alpha test key, kept in the repo so every build installs over the last one.
    // Not for the Play Store. See android/keystore/README.md.
    signingConfigs {
        create("test") {
            storeFile = file("../keystore/test.jks")
            storePassword = "bridgeride-test"
            keyAlias = "bridgeride-test"
            keyPassword = "bridgeride-test"
        }
    }

    buildTypes {
        // Debug: debuggable, so ride files can be pulled over USB. Attached to each release as "-debug".
        getByName("debug") {
            signingConfig = signingConfigs.getByName("test")
        }
        // Release: what Whitney rides with since 0.2.1. R8 shrinks and optimizes the code; debug builds of
        // Compose are much slower, so speed is only ever measured on this build. Same test key, so
        // debug and release install over each other and keep the rides.
        getByName("release") {
            signingConfig = signingConfigs.getByName("test")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.play.services.location)
    implementation(libs.maplibre.android)
}
