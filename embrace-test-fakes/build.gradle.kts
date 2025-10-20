plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
}

embrace {
    productionModule.set(false)
}

android {
    namespace = "io.embrace.android.embracesdk.test.fakes"
}

dependencies {
    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-infra"))
    compileOnly(project(":embrace-android-utils"))
    compileOnly(project(":embrace-android-sdk"))
    compileOnly(project(":embrace-android-payload"))
    compileOnly(project(":embrace-android-features"))
    compileOnly(project(":embrace-android-delivery"))
    compileOnly(project(":embrace-internal-api"))
    implementation(project(":embrace-android-otel"))
    implementation(project(":embrace-android-instrumentation-schema"))

    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.java.aliases)

    implementation(libs.junit)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.robolectric)
    implementation(libs.lifecycle.runtime)

    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.api.ext)
    implementation(libs.opentelemetry.kotlin.sdk)
    implementation(libs.opentelemetry.kotlin.compat)
    implementation(libs.opentelemetry.kotlin.semconv)
}
