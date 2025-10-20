plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK Instrumentation API"

android {
    namespace = "io.embrace.android.embracesdk.instrumentation.api"
}

dependencies {
    api(project(":embrace-android-api"))
    api(project(":embrace-android-core")) // TODO: remove this (spanService)
    api(project(":embrace-android-infra"))
}
