plugins {
    id("embrace-prod-jvm-conventions")
    id("org.jetbrains.kotlin.plugin.serialization")
}

description = "Embrace Android SDK: Payload"

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
