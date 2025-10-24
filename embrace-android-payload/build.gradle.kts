plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
    id("com.google.devtools.ksp")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Payload"

dependencies {
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
    testImplementation(libs.junit)
}
