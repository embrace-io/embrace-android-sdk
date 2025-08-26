package io.embrace.internal

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion

fun LibraryExtension.configureAndroidCompileOptions() {
    compileSdk = 36
    defaultConfig.minSdk = 21

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
