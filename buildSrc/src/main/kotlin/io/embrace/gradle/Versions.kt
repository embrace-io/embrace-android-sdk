package io.embrace.gradle

/**
 * Defines dependency versions that are used in the project that cannot be defined in libs.versions.toml
 */
object Versions {
    const val COMPILE_SDK = 34

    const val MIN_SDK = 21

    const val JUNIT = "4.13.2"

    // Kotlin stdlib version used
    const val KOTLIN_EXPOSED = "1.8.22"

    // NOTE: when updating keep this in sync with the version in buildSrc/build.gradle.kts
    // kotlin 1.9 required before any further upgrades
    const val DETEKT = "1.23.6"

    const val NDK = "21.4.7075529"
    const val MOCKK = "1.12.2"
    const val ANDROIDX_TEST = "1.4.0"
    const val ANDROIDX_JUNIT = "1.1.3"
    const val ROBOLECTRIC = "4.12.1"
    const val MOCKWEBSERVER = "4.9.3"
}
