plugins {
    id("embrace-prod-defaults")
}

description = "Embrace Android SDK: Internal API"

apiValidation.validationDisabled = true

android {
    namespace = "io.embrace.android.embracesdk.api.internal"
}

dependencies {
    compileOnly(project(":embrace-android-api"))
}
