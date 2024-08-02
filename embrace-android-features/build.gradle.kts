plugins {
    id("embrace-prod-defaults")
}

description = "Embrace Android SDK: Features"

android {
    namespace = "io.embrace.android.embracesdk.features"
}

dependencies {
    compileOnly(project(":embrace-android-api"))
    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-payload"))
    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    compileOnly(libs.opentelemetry.semconv)
    compileOnly(libs.opentelemetry.semconv.incubating)
    implementation(libs.lifecycle.process)
}

apiValidation.validationDisabled = true
