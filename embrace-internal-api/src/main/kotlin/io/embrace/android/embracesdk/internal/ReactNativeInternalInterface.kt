package io.embrace.android.embracesdk.internal

import android.content.Context

/**
 * Provides an internal interface to Embrace that is intended for use by the React Native SDK as its
 * sole source of communication with the Android SDK.
 * @suppress
 */
interface ReactNativeInternalInterface : EmbraceInternalInterface {

    /**
     * @suppress
     */
    fun logUnhandledJsException(
        name: String,
        message: String,
        type: String?,
        stacktrace: String?,
    )

    /**
     * @suppress
     */
    fun setJavaScriptPatchNumber(number: String?)

    /**
     * @suppress
     */
    fun setReactNativeSdkVersion(version: String?)

    /**
     * @suppress
     */
    fun setReactNativeVersionNumber(version: String?)

    /**
     * Sets the React Native Bundle URL.
     * @param context the context
     * @param url the JavaScript bundle URL
     * @suppress
     */
    fun setJavaScriptBundleUrl(context: Context, url: String)
}
