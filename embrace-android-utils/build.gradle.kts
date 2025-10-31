plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Utils"

android {
    namespace = "io.embrace.android.embracesdk.utils"
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(project(":embrace-android-infra"))

    testImplementation(libs.robolectric)
}
