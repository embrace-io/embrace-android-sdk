package io.embrace.gradle

/**
 * Defines dependency versions that are used in the project.
 */
object Versions {
    @JvmField
    val compileSdk = 33

    @JvmField
    val minSdk = 21

    @JvmField
    val junit = "4.13.2"

    @JvmField
    val kotlin = "1.7.21"

    // kotin library exposed to the customer
    @JvmField
    val kotlinExposed = "1.4.32"

    @JvmField
    val dokka = "1.7.10"

    @JvmField
    val detekt = "1.23.4"

    @JvmField
    val binaryCompatValidator = "0.12.1"

    @JvmField
    val agp = "7.3.0"

    @JvmField
    val lint = "30.1.0"

    @JvmField
    val ndk = "21.4.7075529"

    @JvmField
    val openTelemetry = "1.29.0"

    @JvmField
    val moshi = "1.12.0"
}
