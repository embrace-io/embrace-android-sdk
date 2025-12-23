package io.embrace.android.embracesdk.internal.config

/**
 * Specifies the application ID and build ID.
 */
class BuildInfo(

    /**
     * The ID of the particular build, generated at compile-time.
     */
    val buildId: String?,

    /**
     * The BuildType name of the particular build, extracted at compile-time.
     */
    val buildType: String?,

    /**
     * The Flavor name of the particular build, extracted at compile-time.
     */
    val buildFlavor: String?,

    /**
     * The ID of the particular js bundle, generated at compile-time.
     */
    val rnBundleId: String?,

    /**
     * The version name of the particular build, extracted at compile-time.
     */
    val versionName: String,

    /**
     * The version code of the particular build, extracted at compile-time.
     */
    val versionCode: String,

    /**
     * The package name of the particular build, extracted at compile-time.
     */
    val packageName: String,
)
