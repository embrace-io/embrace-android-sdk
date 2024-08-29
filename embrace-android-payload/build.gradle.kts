plugins {
    id("embrace-prod-defaults")
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

    implementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-core"))
    testImplementation(project(":embrace-android-payload"))
    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.api)
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.opentelemetry.semconv)
    testImplementation(libs.opentelemetry.semconv.incubating)
}
