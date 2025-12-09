package io.embrace.android.embracesdk.internal.api.delegate

import android.content.Context
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface

internal class NoopReactNativeInternalInterface(
    private val delegate: EmbraceInternalInterface,
) : ReactNativeInternalInterface, EmbraceInternalInterface by delegate {

    override fun logUnhandledJsException(name: String, message: String, type: String?, stacktrace: String?) {}

    override fun setJavaScriptPatchNumber(number: String?) {}

    override fun setReactNativeSdkVersion(version: String?) {}

    override fun setReactNativeVersionNumber(version: String?) {}

    override fun setJavaScriptBundleUrl(context: Context, url: String) {}
}
