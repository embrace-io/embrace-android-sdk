plugins {
    id("com.android.application")
    id("io.embrace.swazzler")
    id("io.embrace.android.testplugin")
    id("org.jetbrains.kotlin.android")
}

repositories {
    google()
    mavenCentral()
    mavenLocal()
}

integrationTest.configureAndroidProject(project)

embrace {
    autoAddEmbraceDependencies.set(true)
}

android {
    compileSdk = 34

    defaultConfig {
        targetSdk = 34
    }
}
