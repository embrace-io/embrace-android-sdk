package io.embrace.android.embracesdk

import android.content.Context
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface

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

    fun setReactNativeSdkVersion(version: String?)

    /**
     * See [Embrace.setReactNativeVersionNumber]
     */
    fun setReactNativeVersionNumber(version: String?)

    /**
     * See [Embrace.setJavaScriptBundleURL]
     */
    fun setJavaScriptBundleUrl(context: Context, url: String)

    /**
     * Logs a React Native Redux Action - this is not intended for public use.
     */
    fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String
    )

    /**
     * Logs the fact that a particular view was entered.
     *
     * If the previously logged view has the same name, a duplicate view breadcrumb will not be
     * logged.
     *
     * @param screen the name of the view to log
     */
    fun logRnView(screen: String)
}
