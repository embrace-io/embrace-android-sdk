plugins {
    id("embrace-defaults")
    id("com.google.devtools.ksp")
}

description = "Embrace Android SDK: Payload"

android {
    namespace = "io.embrace.android.embracesdk.payload"
}

apiValidation.validationDisabled = true

dependencies {
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
}
