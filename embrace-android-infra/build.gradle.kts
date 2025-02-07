plugins {
    id("embrace-prod-defaults")
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK: Infra"

android {
    namespace = "io.embrace.android.embracesdk.infra"
}

apiValidation.validationDisabled = true
