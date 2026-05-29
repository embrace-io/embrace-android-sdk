plugins {
    id("com.google.devtools.ksp")
    id("embrace-prod-jvm-conventions")
    id("org.jetbrains.kotlin.plugin.serialization")
}

description = "Embrace Android SDK: Payload"

dependencies {
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
