package io.embrace.android.embracesdk

/**
 * Provides an internal interface to Embrace that is intended for use by Unity as its
 * sole source of communication with the Android SDK.
 */
internal interface UnityInternalInterface : EmbraceInternalInterface {

    /**
     * See [Embrace.setUnityMetaData]
     */
    fun setUnityMetaData(unityVersion: String?, buildGuid: String?, unitySdkVersion: String?)

    /**
     * See [Embrace.logUnhandledUnityException]
     */
    fun logUnhandledUnityException(
        name: String,
        message: String,
        stacktrace: String?
    )

    /**
     * See [Embrace.logHandledUnityException]
     */
    fun logHandledUnityException(
        name: String,
        message: String,
        stacktrace: String?
    )
}
