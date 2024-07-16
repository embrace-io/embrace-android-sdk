package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface

/**
 * Provides an internal interface to Embrace that is intended for use by Flutter as its
 * sole source of communication with the Android SDK.
 * @suppress
 */
@InternalApi
public interface FlutterInternalInterface : EmbraceInternalInterface {

    /**
     * Sets the Embrace Flutter SDK version - this is not intended for public use.
     * @suppress
     */
    public fun setEmbraceFlutterSdkVersion(version: String?)

    /**
     * Sets the Dart version - this is not intended for public use.
     * @suppress
     */
    public fun setDartVersion(version: String?)

    /**
     * Logs a handled Dart error to the Embrace SDK - this is not intended for public use.
     * @suppress
     */
    public fun logHandledDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?
    )

    /**
     * Logs an unhandled Dart error to the Embrace SDK - this is not intended for public use.
     * @suppress
     */
    public fun logUnhandledDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?
    )
}
