plugins {
    id("embrace-prod-android-conventions")
}

description = "Embrace Android SDK: Core"

android {
    namespace = "io.embrace.android.embracesdk.core"
    defaultConfig {
        // For library projects only, the BuildConfig.VERSION_NAME and BuildConfig.VERSION_CODE properties have been removed from the generated BuildConfig class
        //
        // https://developer.android.com/studio/releases/gradle-plugin#version_properties_removed_from_buildconfig_class_in_library_projects
        buildConfigField("String", "VERSION_NAME", "\"${version}\"")
        buildConfigField("String", "VERSION_CODE", "\"${53}\"")
    }
    buildFeatures {
        buildConfig = true
    }
    defaultConfig.consumerProguardFiles("embrace-proguard.cfg")
}

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-delivery"))
    implementation(project(":embrace-android-envelope"))
    implementation(project(":embrace-android-infra"))
    implementation(project(":embrace-android-utils"))
    implementation(project(":embrace-internal-api"))
    implementation(project(":embrace-android-otel"))
    implementation(project(":embrace-android-config"))
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-instrumentation-schema"))
    implementation(project(":embrace-android-instrumentation-network-common"))
    implementation(project(":embrace-android-instrumentation-crash-ndk"))
    implementation(project(":embrace-android-instrumentation-anr"))
    compileOnly(project(":embrace-android-api"))

    implementation(libs.androidx.annotation)
    implementation(libs.lifecycle.process)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.semconv)

    testImplementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-test-fakes"))
    testImplementation(project(":embrace-android-config-fakes"))
    testImplementation(project(":embrace-android-delivery-fakes"))
    testImplementation(project(":embrace-android-otel-fakes"))
    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.api)
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.lifecycle.runtime)
    testImplementation(libs.lifecycle.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(kotlin("reflect"))
}
