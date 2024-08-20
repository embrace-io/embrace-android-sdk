plugins {
    id("embrace-prod-defaults")
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

apiValidation.validationDisabled = true

dependencies {
    implementation(project(":embrace-android-payload"))
    compileOnly(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-payload"))
    testImplementation(project(":embrace-android-api"))
    testImplementation(project(":embrace-android-features"))

    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    compileOnly(libs.opentelemetry.semconv)
    compileOnly(libs.opentelemetry.semconv.incubating)
    compileOnly(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.process)


    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.api)
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.opentelemetry.semconv)
    testImplementation(libs.opentelemetry.semconv.incubating)
    testImplementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.process)
}
