plugins {
    id("com.android.application")
    id("io.embrace.gradle")
    id("io.embrace.android.testplugin")
    id("org.jetbrains.kotlin.android")
}

integrationTest.configureAndroidProject(project)

repositories {
    google()
    mavenCentral()
    mavenLocal()
}

embrace {
    autoAddEmbraceDependencies.set(true)
}
