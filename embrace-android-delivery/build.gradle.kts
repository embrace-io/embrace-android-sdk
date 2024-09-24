plugins {
    id("embrace-prod-defaults")
}

description = "Embrace Android SDK: Delivery"

android {
    namespace = "io.embrace.android.embracesdk.delivery"
}

apiValidation.validationDisabled = true

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-core"))
    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.api)
    testImplementation(libs.opentelemetry.sdk)
}
