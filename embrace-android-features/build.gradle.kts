plugins {
    id("embrace-prod-defaults")
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK: Features"

android {
    namespace = "io.embrace.android.embracesdk.features"
}

dependencies {
    compileOnly(project(":embrace-android-api"))
    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-infra"))
    compileOnly(project(":embrace-android-payload"))
    compileOnly(project(":embrace-internal-api"))
    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    compileOnly(libs.opentelemetry.semconv)
    compileOnly(libs.opentelemetry.semconv.incubating)
    implementation(libs.lifecycle.process)

    testImplementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-core"))
    testImplementation(project(":embrace-android-infra"))
    testImplementation(project(":embrace-android-payload"))
    testImplementation(project(":embrace-internal-api"))
    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.api)
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.opentelemetry.semconv)
    testImplementation(libs.opentelemetry.semconv.incubating)
    testImplementation(libs.lifecycle.process)
    testImplementation(libs.protobuf.java)
    testImplementation(libs.protobuf.java.util)
}

apiValidation.validationDisabled = true
