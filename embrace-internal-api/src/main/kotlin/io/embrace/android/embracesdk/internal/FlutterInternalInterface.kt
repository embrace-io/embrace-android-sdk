package io.embrace.android.embracesdk.internal

/**
 * Provides an internal interface to Embrace that is intended for use by Flutter as its
 * sole source of communication with the Android SDK.
 * @suppress
 */
interface FlutterInternalInterface : EmbraceInternalInterface {

    /**
     * Sets the Embrace Flutter SDK version - this is not intended for public use.
     * @suppress
     */
    fun setEmbraceFlutterSdkVersion(version: String?)

    /**
     * Sets the Dart version - this is not intended for public use.
     * @suppress
     */
    fun setDartVersion(version: String?)
}
