plugins {
    id("embrace-common-conventions")
    id("embrace-android-conventions")
    id("embrace-publishing-conventions")
    id("org.jetbrains.kotlinx.kover")
}

android {
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        aarMetadata.minCompileSdk = project.findVersion("minCompileSdk").toInt()
    }

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

dependencies {
    add("testImplementation", findLibrary("junit"))
    add("testImplementation", findLibrary("androidx.test.core"))
    add("testImplementation", findLibrary("androidx.test.junit"))
}
