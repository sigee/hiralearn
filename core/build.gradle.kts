// Pure Kotlin module: kana catalogue, spaced-repetition scheduler, study-session engine and
// handwriting grader. No Android dependencies, so all learning logic is unit-testable on the JVM
// and can later be shared with a sync backend.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.pitest)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}

// Mutation testing: `./gradlew :core:pitest`, report in core/build/reports/pitest/index.html.
// PIT changes the compiled code (flips conditions, drops calls, alters constants) and checks that a test fails
// for each change, so it measures whether the tests really pin down the learning logic, not just run it.
pitest {
    pitestVersion.set(libs.versions.pitest)
    targetClasses.set(listOf("hu.sigee.hiralearn.core.*"))
    targetTests.set(listOf("hu.sigee.hiralearn.core.*"))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    // Kotlin's null-check intrinsics and compiler-generated data-class members are not our logic.
    avoidCallsTo.set(listOf("kotlin.jvm.internal"))
    excludedMethods.set(listOf("toString", "hashCode", "equals", "component*", "copy", "copy\$default"))
    timeoutConstInMillis.set(10_000)
    // Fail the build if the kill rate drops. The survivors left at ~92% are equivalent mutants (float `<` vs `<=`, inlined
    // stdlib code); raise this when you kill more.
    mutationThreshold.set(90)
}
