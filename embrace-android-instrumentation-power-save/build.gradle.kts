plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK Instrumentation: Power Save"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.powersave"
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
}
