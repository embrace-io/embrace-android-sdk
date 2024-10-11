plugins {
    id("embrace-prod-defaults")
}

description = "Embrace Android SDK: Delivery"

android {
    namespace = "io.embrace.android.embracesdk.delivery"
}

apiValidation.validationDisabled = true

dependencies {
    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
}
