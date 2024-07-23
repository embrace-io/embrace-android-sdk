plugins {
    id("embrace-defaults")
}

description = "Embrace Android SDK: Core"

android {
    namespace = "io.embrace.android.embracesdk.core"
}

apiValidation.validationDisabled = true

dependencies {
    implementation(project(":embrace-android-payload"))
    compileOnly(project(":embrace-android-api"))
    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.semconv)
    compileOnly(libs.opentelemetry.semconv.incubating)
    compileOnly(libs.lifecycle.common.java8)
}
