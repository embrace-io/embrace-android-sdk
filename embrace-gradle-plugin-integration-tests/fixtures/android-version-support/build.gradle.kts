plugins {
    id("com.android.application")
    id("io.embrace.gradle")
    id("io.embrace.android.testplugin")
    id("org.jetbrains.kotlin.android") apply false
}

val agpVersion = project.findProperty("agp_version")?.toString() ?: "8.0.0"
val agpMajorVersion = agpVersion.split(".").firstOrNull()?.toIntOrNull() ?: 8

// For AGP 8.x, apply the Kotlin plugin. AGP 9+ has built-in Kotlin support.
if (agpMajorVersion < 9) {
    plugins.apply("org.jetbrains.kotlin.android")
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
