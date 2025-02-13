include(":app")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
    plugins {
        val agpVersion = extra["agp_version"] as String
        val snapshotVersion = extra["plugin_snapshot_version"] as String

        id("com.android.application").version(agpVersion)
        id("io.embrace.swazzler").version(snapshotVersion)
        id("io.embrace.android.testplugin").version(snapshotVersion)
    }
}
