package io.embrace.android.embracesdk

/**
 * Provides an internal interface to Embrace that is intended for use by Flutter as its
 * sole source of communication with the Android SDK.
 */
internal interface FlutterInternalInterface : EmbraceInternalInterface {

    /**
     * See [Embrace.setEmbraceFlutterSdkVersion]
     */
    fun setEmbraceFlutterSdkVersion(version: String?)

    /**
     * See [Embrace.setDartVersion]
     */
    fun setDartVersion(version: String?)

    /**
     * See [Embrace.logHandledDartException]
     */
    fun logHandledDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?
    )

    /**
     * See [Embrace.logUnhandledDartException]
     */
    fun logUnhandledDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?
    )
}
