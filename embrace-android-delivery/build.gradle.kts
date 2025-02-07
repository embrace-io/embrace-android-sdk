plugins {
    id("embrace-prod-defaults")
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK: Delivery"

android {
    namespace = "io.embrace.android.embracesdk.delivery"
}

apiValidation.validationDisabled = true

dependencies {
    implementation(libs.okhttp)
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
}
