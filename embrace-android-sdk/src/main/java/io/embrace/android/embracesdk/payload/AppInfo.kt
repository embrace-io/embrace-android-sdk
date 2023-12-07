package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class AppInfo(
    /**
     * The version of the app which has embedded the Embrace SDK.
     */
    @SerializedName("v")
    val appVersion: String? = null,
    /**
     * The framework used by the app.
     */
    @SerializedName("f")
    val appFramework: Int? = null,

    /**
     * A unique ID for the build which is generated at build time. This is written to a JSON file in
     * the build directory and read by {@link BuildInfo}.
     */
    @SerializedName("bi")
    val buildId: String? = null,

    /**
     * The build type name. This is written to a JSON file in the build directory and read by
     * {@link BuildInfo}.
     */
    @SerializedName("bt")
    val buildType: String? = null,

    /**
     * The flavor name. This is written to a JSON file in the build directory and read by
     * {@link BuildInfo}.
     */
    @SerializedName("fl")
    val buildFlavor: String? = null,

    /**
     * The name of the environment, i.e. dev or prod, determined by whether this is a debug build.
     */
    @SerializedName("e")
    val environment: String? = null,

    /**
     * Whether the app was updated since the previous launch.
     */
    @SerializedName("vu")
    val appUpdated: Boolean? = null,

    /**
     * Whether the app was updated since the previous launch.
     */
    @SerializedName("vul")
    val appUpdatedThisLaunch: Boolean? = null,

    /**
     * The app bundle version.
     */
    @SerializedName("bv")
    val bundleVersion: String? = null,

    /**
     * Whether the OS was updated since the last launch.
     */
    @SerializedName("ou")
    val osUpdated: Boolean? = null,

    /**
     * Whether the OS was updated since the last launch.
     */
    @SerializedName("oul")
    val osUpdatedThisLaunch: Boolean? = null,

    /**
     * The version number of the Embrace SDK.
     */
    @SerializedName("sdk")
    val sdkVersion: String? = null,

    /**
     * The simple version number of the Embrace SDK.
     */
    @SerializedName("sdc")
    val sdkSimpleVersion: String? = null,

    /**
     * The react native bundle hashed.
     */
    @SerializedName("rn")
    val reactNativeBundleId: String? = null,

    /**
     * The java script patch number.
     */
    @SerializedName("jsp")
    val javaScriptPatchNumber: String? = null,

    /**
     * The react native version number.
     */
    @SerializedName("rnv")
    val reactNativeVersion: String? = null,

    /**
     * The version number of the platform (e.g. Unity 2021)
     */
    @SerializedName("unv")
    val hostedPlatformVersion: String? = null,

    /**
     * The unity build id number.
     */
    @SerializedName("ubg")
    val buildGuid: String? = null,

    /**
     * The version number of the hosted SDK (e.g. Embrace Unity 1.7.0)
     */
    @SerializedName("usv")
    val hostedSdkVersion: String? = null,
)
