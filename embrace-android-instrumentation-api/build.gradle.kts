plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Instrumentation API"

android {
    namespace = "io.embrace.android.embracesdk.internal.instrumentation"
    defaultConfig.consumerProguardFiles("embrace-proguard.cfg")
}

dependencies {
    api(project(":embrace-android-instrumentation-schema"))
    api(project(":embrace-android-infra"))
    api(project(":embrace-android-utils"))
    api(project(":embrace-android-config"))
    api(project(":embrace-android-payload"))
    api(libs.opentelemetry.kotlin.semconv)
    api(libs.androidx.annotation)

    testImplementation(project(":embrace-test-common"))
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
}
