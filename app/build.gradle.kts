plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.vagrant326.atvlasttrace"
    compileSdk = 35

    // Pinned to what the dev image installs; AGP 8.7.3 would otherwise fetch 34.0.0 on every run.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "io.github.vagrant326.atvlasttrace"
        minSdk = 23
        targetSdk = 35
        versionCode = (providers.gradleProperty("versionCode").orNull ?: "1").toInt()
        versionName = providers.gradleProperty("versionName").orNull ?: "0.0.0-dev"
        buildConfigField("String", "GAME_URL", "\"https://ostatnislad.rastwork.pl\"")
    }

    // Same two channels as the keyboards: develop publishes a dev app that installs alongside
    // the production one instead of replacing it.
    flavorDimensions += "channel"
    productFlavors {
        create("prod") {
            dimension = "channel"
        }
        create("dev") {
            dimension = "channel"
            applicationIdSuffix = ".dev"
        }
    }

    val keystoreFile = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (!keystoreFile.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    jvmToolchain(17)
}
