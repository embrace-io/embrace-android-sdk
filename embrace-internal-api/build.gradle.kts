plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Internal API"

android {
    namespace = "io.embrace.android.embracesdk.api.internal"
    defaultConfig.consumerProguardFiles("embrace-proguard.cfg")
}

dependencies {
    compileOnly(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-api"))
    testImplementation(libs.robolectric)
}
