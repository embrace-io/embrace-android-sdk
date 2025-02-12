package io.embrace.internal

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion

fun LibraryExtension.configureAndroidCompileOptions() {
    compileSdk = 35
    defaultConfig.minSdk = 21

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
