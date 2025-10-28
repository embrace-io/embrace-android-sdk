package io.embrace.internal

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

@Suppress("unused")
fun Project.configureAndroidProductionModule(android: LibraryExtension) {
    android.apply {
        useLibrary("android.test.runner")
        useLibrary("android.test.base")
        useLibrary("android.test.mock")

        defaultConfig {
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            aarMetadata.minCompileSdk = resolveVersionFromCatalog("minCompileSdk").toInt()
        }

        configureTestOptions(this)

        buildTypes {
            named("release") {
                isMinifyEnabled = false
            }
        }

        sourceSets {
            getByName("test").java.srcDir("src/integrationTest/java")
            getByName("test").kotlin.srcDir("src/integrationTest/kotlin")
            getByName("test").resources.srcDir("src/integrationTest/resources")
        }
    }
}
