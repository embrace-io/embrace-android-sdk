plugins {
    id("com.android.application")
    id("io.embrace.swazzler")
    id("io.embrace.android.testplugin")
    id("org.jetbrains.kotlin.android")
}

integrationTest.configureAndroidProject(project)

android {
    compileSdk = 33

    defaultConfig {
        targetSdk = 33
    }
}
