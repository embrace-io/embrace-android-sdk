plugins {
    id("com.android.application")
    id("io.embrace.gradle")
    id("io.embrace.android.testplugin")
    id("org.jetbrains.kotlin.android")
}

integrationTest.configureAndroidProject(project)
integrationTest.configureDesugaring(project)

repositories {
    google()
    mavenCentral()
    mavenLocal()
}
