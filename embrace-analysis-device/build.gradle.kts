// embrace-analysis-device: what an attached Android device IS, read over adb. The adb wrapper with a
// real timeout, the topology probe (clusters, RAM class, thermal sensors), the reference-set profile
// (API level, release, vendor, SoC, RAM, storage) and the redacted provenance a corpus submission
// carries. Nothing here knows about SDK init; any adb-driven tool can use it.
//
// The serialization plugin is applied because DeviceProfile and the topology types are @Serializable -
// they are written into records and read back.
plugins {
    id("embrace-analysis-conventions")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":embrace-analysis-common"))
    testImplementation(project(":embrace-analysis-test-fixtures"))
}
