package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public data class AppInfo(

    /**
     * The version of the app which has embedded the Embrace SDK.
     */
    @Json(name = "v")
    val appVersion: String? = null,
    /**
     * The framework used by the app.
     */
    @Json(name = "f")
    val appFramework: Int? = null,

    /**
     * A unique ID for the build which is generated at build time. This is written to a JSON file in
     * the build directory and read by {@link BuildInfo}.
     */
    @Json(name = "bi")
    val buildId: String? = null,

    /**
     * The build type name. This is written to a JSON file in the build directory and read by
     * {@link BuildInfo}.
     */
    @Json(name = "bt")
    val buildType: String? = null,

    /**
     * The flavor name. This is written to a JSON file in the build directory and read by
     * {@link BuildInfo}.
     */
    @Json(name = "fl")
    val buildFlavor: String? = null,

    /**
     * The name of the environment, i.e. dev or prod, determined by whether this is a debug build.
     */
    @Json(name = "e")
    val environment: String? = null,

    /**
     * Whether the app was updated since the previous launch.
     */
    @Json(name = "vu")
    val appUpdated: Boolean? = null,

    /**
     * Whether the app was updated since the previous launch.
     */
    @Json(name = "vul")
    val appUpdatedThisLaunch: Boolean? = null,

    /**
     * The app bundle version.
     */
    @Json(name = "bv")
    val bundleVersion: String? = null,

    /**
     * Whether the OS was updated since the last launch.
     */
    @Json(name = "ou")
    val osUpdated: Boolean? = null,

    /**
     * Whether the OS was updated since the last launch.
     */
    @Json(name = "oul")
    val osUpdatedThisLaunch: Boolean? = null,

    /**
     * The version number of the Embrace SDK.
     */
    @Json(name = "sdk")
    val sdkVersion: String? = null,

    /**
     * The simple version number of the Embrace SDK.
     */
    @Json(name = "sdc")
    val sdkSimpleVersion: String? = null,

    /**
     * The react native bundle hashed.
     */
    @Json(name = "rn")
    val reactNativeBundleId: String? = null,

    /**
     * The java script patch number.
     */
    @Json(name = "jsp")
    val javaScriptPatchNumber: String? = null,

    /**
     * The react native version number.
     */
    @Json(name = "rnv")
    val reactNativeVersion: String? = null,

    /**
     * The version number of the platform (e.g. Unity 2021)
     */
    @Json(name = "unv")
    val hostedPlatformVersion: String? = null,

    /**
     * The unity build id number.
     */
    @Json(name = "ubg")
    val buildGuid: String? = null,

    /**
     * The version number of the hosted SDK (e.g. Embrace Unity 1.7.0)
     */
    @Json(name = "usv")
    val hostedSdkVersion: String? = null,
)
