import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/*
 * Upload key for Play releases, from keystore.properties in the project root (gitignored) or, on CI, from
 * environment variables. Without it, release builds are left unsigned; Play App Signing re-signs uploads anyway.
 */
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

val uploadStoreFile = signingValue("storeFile", "HIRALEARN_STORE_FILE")

android {
    namespace = "hu.sigee.hiralearn"
    // Compose 1.13 needs API 37.1+; minor API levels need the block form.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "hu.sigee.hiralearn"
        minSdk = 26
        targetSdk = 37
        // Play never accepts a versionCode twice. The release workflow sets it from its run number
        // (-Phiralearn.versionCode), so local builds stay at 1 and nobody bumps it by hand.
        versionCode = providers.gradleProperty("hiralearn.versionCode").orNull?.toInt() ?: 1
        // The release tag must match this (v0.1.0); the workflow checks.
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(uploadStoreFile)
                storePassword = signingValue("storePassword", "HIRALEARN_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "HIRALEARN_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "HIRALEARN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // Lists English, Hungarian and German in the system's per-app language settings.
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.DEBUG gates the developer tools in Settings
    }
}

room {
    // Exported schemas let us write tested migrations when sync support changes the database.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)

    // UI tests on a device or emulator: ./gradlew :app:connectedDebugAndroidTest
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
