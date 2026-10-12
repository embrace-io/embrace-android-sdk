pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri(providers.gradleProperty("integration_repo").get()) }
    }
    plugins {
        val agpVersion = extra["agp_version"] as String
        val snapshotVersion = extra["plugin_snapshot_version"] as String
        val kotlinVersion = extra["kotlin_version"] as String

        id("com.android.application").version(agpVersion)
        id("com.android.library").version(agpVersion)
        id("org.jetbrains.kotlin.android").version(kotlinVersion)
        id("io.embrace.gradle").version(snapshotVersion)
        id("io.embrace.android.testplugin").version(snapshotVersion)
    }
}
