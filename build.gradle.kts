// Top-level build file. Plugins are declared here (not applied) so that all modules
// share one classloader and the same Kotlin Gradle plugin version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.pitest) apply false
}
