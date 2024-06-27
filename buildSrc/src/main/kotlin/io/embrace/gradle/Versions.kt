package io.embrace.gradle

/**
 * Defines dependency versions that are used in the project that cannot be defined in libs.versions.toml
 */
object Versions {
    const val COMPILE_SDK = 34

    const val MIN_SDK = 21

    const val JUNIT = "4.13.2"

    // kotin library exposed to the customer
    const val KOTLIN_EXPOSED = "1.4.32"

    // NOTE: when updating keep this in sync with the version in buildSrc/build.gradle.kts
    // kotlin 1.9 required before any further upgrades
    const val DETEKT = "1.23.0"

    const val NDK = "21.4.7075529"
}
