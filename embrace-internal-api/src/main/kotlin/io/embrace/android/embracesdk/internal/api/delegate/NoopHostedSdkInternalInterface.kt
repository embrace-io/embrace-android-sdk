package io.embrace.android.embracesdk.internal.api.delegate

import android.content.Context
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.UnityInternalInterface

internal object NoopHostedSdkInternalInterface :
    EmbraceInternalInterface,
    ReactNativeInternalInterface,
    UnityInternalInterface,
    FlutterInternalInterface {

    override fun isNetworkSpanForwardingEnabled(): Boolean = false

    override fun addEnvelopeResource(key: String, value: String) {}

    override fun setHostedSdkVersion(version: String?) {}

    override fun setHostedPlatformVersion(version: String?) {}

    override fun logUnhandledJsException(name: String, message: String, type: String?, stacktrace: String?) {}

    override fun setJavaScriptPatchNumber(number: String?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setReactNativeSdkVersion(version: String?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setReactNativeVersionNumber(version: String?) {}

    override fun setJavaScriptBundleUrl(context: Context, url: String) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setUnityMetaData(unityVersion: String?, buildGuid: String?, unitySdkVersion: String?) {}

    override fun setUnityBuildGuid(buildGuid: String?) {}

    override fun logUnhandledUnityException(name: String, message: String, stacktrace: String?) {}

    override fun logHandledUnityException(name: String, message: String, stacktrace: String?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setEmbraceFlutterSdkVersion(version: String?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun setDartVersion(version: String?) {}
}
