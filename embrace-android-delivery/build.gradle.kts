plugins {
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK: Delivery"

android {
    namespace = "io.embrace.android.embracesdk.delivery"
}

dependencies {
    implementation(libs.okhttp)
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
}
