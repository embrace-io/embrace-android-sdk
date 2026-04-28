plugins {
    id("embrace-public-api-conventions")
    alias(libs.plugins.kotlin.compose)
}

description = "Embrace Android SDK: Compose Navigation Support"

android {
    defaultConfig {
        namespace = "io.embrace.android.embracesdk.instrumentation.compose.navigation"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
    implementation(project(":embrace-android-instrumentation-navigation"))
    implementation(libs.androidx.navigation.fragment)
    compileOnly(libs.androidx.navigation.compose)

    testImplementation(project(":embrace-android-instrumentation-api-fakes"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.navigation.testing)
    testCompileOnly(libs.compose.runtime)

    add("kotlinCompilerPluginClasspath", libs.kotlin.compose.compiler.plugin)
}
