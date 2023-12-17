package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface

/**
 * Provides an internal interface to Embrace that is intended for use by Flutter as its
 * sole source of communication with the Android SDK.
 */
internal interface FlutterInternalInterface : EmbraceInternalInterface {

    /**
     * Sets the Embrace Flutter SDK version - this is not intended for public use.
     */
    fun setEmbraceFlutterSdkVersion(version: String?)

    /**
     * Sets the Dart version - this is not intended for public use.
     */
    fun setDartVersion(version: String?)

    /**
     * Logs a handled Dart error to the Embrace SDK - this is not intended for public use.
     */
    fun logHandledDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?
    )

    /**
     * Logs an unhandled Dart error to the Embrace SDK - this is not intended for public use.
     */
    fun logUnhandledDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?
    )
}
