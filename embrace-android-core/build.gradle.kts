plugins {
    id("io.embrace.internal.build-logic")
}

embrace {
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
}

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-delivery"))
    implementation(project(":embrace-android-infra"))
    implementation(project(":embrace-internal-api"))
    compileOnly(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-payload"))
    testImplementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-features"))

    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    compileOnly(libs.opentelemetry.semconv)
    compileOnly(libs.opentelemetry.semconv.incubating)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.process)
    implementation(libs.okhttp)

    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.api)
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.opentelemetry.semconv)
    testImplementation(libs.opentelemetry.semconv.incubating)
    testImplementation(libs.lifecycle.runtime)
    testImplementation(libs.lifecycle.process)
    testImplementation(libs.lifecycle.testing)
    testImplementation(libs.kotlin.reflect)
}
