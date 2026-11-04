package io.embrace.android.embracesdk.internal.api.delegate

import android.content.Context
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.UnityInternalInterface

internal class ForwardingInternalInterface(
    private val target: () -> InternalInterfaceApi,
) : EmbraceInternalInterface,
    ReactNativeInternalInterface,
    UnityInternalInterface,
    FlutterInternalInterface {

    override fun isNetworkSpanForwardingEnabled(): Boolean =
        target().internalInterface.isNetworkSpanForwardingEnabled()

    override fun addEnvelopeResource(key: String, value: String) =
        target().internalInterface.addEnvelopeResource(key, value)

    override fun setHostedSdkVersions(sdkVersion: String?, platformVersion: String?) =
        target().internalInterface.setHostedSdkVersions(sdkVersion, platformVersion)

    override fun logUnhandledJsException(name: String, message: String, type: String?, stacktrace: String?) =
        target().reactNativeInternalInterface.logUnhandledJsException(name, message, type, stacktrace)

    override fun setJavaScriptPatchNumber(number: String?) =
        target().reactNativeInternalInterface.setJavaScriptPatchNumber(number)

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun setReactNativeSdkVersion(version: String?) =
        target().reactNativeInternalInterface.setReactNativeSdkVersion(version)

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun setReactNativeVersionNumber(version: String?) =
        target().reactNativeInternalInterface.setReactNativeVersionNumber(version)

    override fun setJavaScriptBundleUrl(context: Context, url: String) =
        target().reactNativeInternalInterface.setJavaScriptBundleUrl(context, url)

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun setUnityMetaData(unityVersion: String?, buildGuid: String?, unitySdkVersion: String?) =
        target().unityInternalInterface.setUnityMetaData(unityVersion, buildGuid, unitySdkVersion)

    override fun setUnityBuildGuid(buildGuid: String?) =
        target().unityInternalInterface.setUnityBuildGuid(buildGuid)

    override fun logUnhandledUnityException(name: String, message: String, stacktrace: String?) =
        target().unityInternalInterface.logUnhandledUnityException(name, message, stacktrace)

    override fun logHandledUnityException(name: String, message: String, stacktrace: String?) =
        target().unityInternalInterface.logHandledUnityException(name, message, stacktrace)

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun setEmbraceFlutterSdkVersion(version: String?) =
        target().flutterInternalInterface.setEmbraceFlutterSdkVersion(version)

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun setDartVersion(version: String?) =
        target().flutterInternalInterface.setDartVersion(version)
}
