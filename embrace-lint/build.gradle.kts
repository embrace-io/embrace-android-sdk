import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("embrace-jvm-conventions")
    id("java-library")
    id("com.android.lint")
}

// The Android lint tooling (32.3.0+, i.e. AGP 8.13+) requires JVM 17. Override the JVM 11 target
// inherited from embrace-jvm-conventions for this build-time-only module so the lint dependencies
// resolve. Lint checks run in the host's JDK, so this does not affect the SDK's runtime target.
kotlin.compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

dependencies {
    compileOnly(libs.lint.api)
    testCompileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
