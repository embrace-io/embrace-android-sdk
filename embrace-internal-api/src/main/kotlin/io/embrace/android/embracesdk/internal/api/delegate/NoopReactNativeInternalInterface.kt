package io.embrace.android.embracesdk.internal.api.delegate

import android.content.Context
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface

internal class NoopReactNativeInternalInterface(
    private val delegate: EmbraceInternalInterface,
) : ReactNativeInternalInterface, EmbraceInternalInterface by delegate {

    override fun logUnhandledJsException(name: String, message: String, type: String?, stacktrace: String?) {}

    override fun logHandledJsException(
        name: String,
        message: String,
        properties: Map<String, Any>,
        stacktrace: String?,
    ) {
    }

    override fun setJavaScriptPatchNumber(number: String?) {}

    override fun setReactNativeSdkVersion(version: String?) {}

    override fun setReactNativeVersionNumber(version: String?) {}

    override fun setJavaScriptBundleUrl(context: Context, url: String) {}

    override fun setCacheableJavaScriptBundleUrl(context: Context, url: String, didUpdate: Boolean) {}

    override fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String,
    ) {
    }

    override fun logRnView(screen: String) {}
}
