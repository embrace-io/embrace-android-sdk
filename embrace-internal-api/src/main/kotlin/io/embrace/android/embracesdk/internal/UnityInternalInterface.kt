package io.embrace.android.embracesdk.internal

/**
 * Provides an internal interface to Embrace that is intended for use by the Embrace Unity SDK as its
 * sole source of communication with the Android SDK.
 * @suppress
 */
interface UnityInternalInterface : EmbraceInternalInterface {

    /**
     * @suppress
     */
    @Deprecated(
        "Use setHostedPlatformVersion(), setHostedSdkVersion() and setUnityBuildGuid() instead.",
        ReplaceWith(
            "run { setHostedPlatformVersion(unityVersion); setHostedSdkVersion(unitySdkVersion); " +
                "setUnityBuildGuid(buildGuid) }",
        ),
    )
    fun setUnityMetaData(unityVersion: String?, buildGuid: String?, unitySdkVersion: String?)

    /**
     * Sets the Unity build GUID. Null or empty values are ignored.
     * @suppress
     */
    fun setUnityBuildGuid(buildGuid: String?)

    /**
     * @suppress
     */
    fun logUnhandledUnityException(
        name: String,
        message: String,
        stacktrace: String?,
    )

    /**
     * @suppress
     */
    fun logHandledUnityException(
        name: String,
        message: String,
        stacktrace: String?,
    )
}
