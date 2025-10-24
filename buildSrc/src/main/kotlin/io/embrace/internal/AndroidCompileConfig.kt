package io.embrace.internal

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

fun LibraryExtension.configureAndroidCompileOptions(project: Project) {
    compileSdk = project.resolveVersionFromCatalog("compileSdk").toInt()
    defaultConfig.minSdk = project.resolveVersionFromCatalog("minSdk").toInt()
    val javaVersion = JavaVersion.VERSION_11

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
}
