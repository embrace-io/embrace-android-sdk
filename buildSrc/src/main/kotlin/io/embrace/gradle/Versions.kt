package io.embrace.gradle

/**
 * Defines dependency versions that are used in the project.
 */
object Versions {
    @JvmField
    val compileSdk = 34

    @JvmField
    val minSdk = 21

    @JvmField
    val junit = "4.13.2"

    // NOTE: when updating keep this in sync with the version in buildSrc/build.gradle.kts
    @JvmField
    val kotlin = "1.7.21"

    // kotin library exposed to the customer
    @JvmField
    val kotlinExposed = "1.4.32"

    @JvmField
    val dokka = "1.9.10"

    // NOTE: when updating keep this in sync with the version in buildSrc/build.gradle.kts
    @JvmField
    val detekt = "1.23.0" // kotlin 1.9 required before any further upgrades

    // NOTE: when updating keep this in sync with the version in buildSrc/build.gradle.kts
    @JvmField
    val agp = "8.3.2"

    @JvmField
    val lint = "30.1.0"

    @JvmField
    val ndk = "21.4.7075529"

    @JvmField
    val openTelemetryCore = "1.35.0"

    @JvmField
    val moshi = "1.12.0"
}
