plugins {
    id("embrace-defaults")
    id("org.jetbrains.kotlin.kapt")
}

description = "Embrace Android SDK: Payload"

android {
    namespace = "io.embrace.android.embracesdk.payload"
}

apiValidation.validationDisabled = true

dependencies {
    implementation(libs.moshi)
    kapt(libs.moshi.kotlin.codegen)
}
