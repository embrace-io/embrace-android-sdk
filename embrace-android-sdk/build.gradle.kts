plugins {
    id("embrace-public-api-conventions")
    id("com.google.devtools.ksp")
}

description = "Embrace Android SDK: Core"

android {
    defaultConfig {
        namespace = "io.embrace.android.embracesdk"
        consumerProguardFiles("embrace-proguard.cfg")
    }
}

dependencies {
    api(project(":embrace-android-api"))
    implementation(project(":embrace-android-infra"))
    implementation(project(":embrace-android-utils"))
    implementation(project(":embrace-android-core"))
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-config"))
    implementation(project(":embrace-android-delivery"))
    implementation(project(":embrace-internal-api"))
    implementation(project(":embrace-android-otel"))
    implementation(project(":embrace-android-instrumentation-schema"))
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-instrumentation-network-common"))

    // automatically included instrumentation
    implementation(project(":embrace-android-instrumentation-anr"))
    implementation(project(":embrace-android-instrumentation-app-exit-info"))
    implementation(project(":embrace-android-instrumentation-crash-jvm"))
    implementation(project(":embrace-android-instrumentation-crash-ndk"))
    implementation(project(":embrace-android-instrumentation-fcm"))
    implementation(project(":embrace-android-instrumentation-huc-lite"))
    implementation(project(":embrace-android-instrumentation-network-status"))
    implementation(project(":embrace-android-instrumentation-okhttp"))
    implementation(project(":embrace-android-instrumentation-power-save"))
    implementation(project(":embrace-android-instrumentation-startup-trace"))
    implementation(project(":embrace-android-instrumentation-taps"))
    implementation(project(":embrace-android-instrumentation-thermal-state"))
    implementation(project(":embrace-android-instrumentation-view"))
    implementation(project(":embrace-android-instrumentation-webview"))

    // lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.process)
    ksp(libs.lifecycle.compiler)
    testImplementation(libs.lifecycle.testing)

    // json
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.profileinstaller)

    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.api.ext)
    implementation(libs.opentelemetry.kotlin.semconv)
    implementation(libs.opentelemetry.kotlin.noop)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    testImplementation(libs.opentelemetry.java.aliases)
    testImplementation(project(":embrace-android-otel-java"))
    testImplementation(project(":embrace-test-fakes"))
    testImplementation(project(":embrace-android-config-fakes"))
    testImplementation(project(":embrace-android-delivery-fakes"))
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(project(":embrace-android-otel-fakes"))
    testImplementation(project(":embrace-android-instrumentation-huc"))
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.robolectric)
    testImplementation(libs.opentelemetry.kotlin.compat)

    lintChecks(project(":embrace-lint"))
}
