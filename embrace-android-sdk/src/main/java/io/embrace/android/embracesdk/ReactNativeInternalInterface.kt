package io.embrace.android.embracesdk

import android.content.Context

/**
 * Provides an internal interface to Embrace that is intended for use by React Native as its
 * sole source of communication with the Android SDK.
 */
internal interface ReactNativeInternalInterface : EmbraceInternalInterface {

    /**
     * See [Embrace.logUnhandledJsException]
     */
    fun logUnhandledJsException(
        name: String,
        message: String,
        type: String?,
        stacktrace: String?
    )

    fun logHandledJsException(
        name: String,
        message: String,
        properties: Map<String, Any>,
        stacktrace: String?
    )

    /**
     * See [Embrace.setJavaScriptPatchNumber]
     */
    fun setJavaScriptPatchNumber(number: String?)

    /**
     * See [Embrace.setReactNativeVersionNumber]
     */
    fun setReactNativeVersionNumber(version: String?)

    /**
     * See [Embrace.setJavaScriptBundleURL]
     */
    fun setJavaScriptBundleUrl(context: Context, url: String)
}
