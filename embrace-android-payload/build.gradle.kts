plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
    id("com.google.devtools.ksp")
}

description = "Embrace Android SDK: Payload"

dependencies {
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
    testImplementation(libs.junit)
}
