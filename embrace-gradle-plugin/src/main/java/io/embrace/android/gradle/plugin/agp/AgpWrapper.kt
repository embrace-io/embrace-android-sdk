package io.embrace.android.gradle.plugin.agp

/**
 * Shim around AGP's DSL. This abstracts away any changes between different AGP versions we support.
 */
interface AgpWrapper {
    val isCoreLibraryDesugaringEnabled: Boolean
    val usesCMake: Boolean
    val usesNdkBuild: Boolean
    val minSdk: Int?
    val version: AgpVersion
}
