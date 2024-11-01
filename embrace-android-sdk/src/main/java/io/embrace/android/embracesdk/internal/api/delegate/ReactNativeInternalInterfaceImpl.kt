package io.embrace.android.embracesdk.internal.api.delegate

import android.content.Context
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.capture.crash.CrashService
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.JsException

internal class ReactNativeInternalInterfaceImpl(
    private val embrace: EmbraceImpl,
    private val impl: EmbraceInternalInterface,
    private val crashService: CrashService,
    private val rnBundleIdTracker: RnBundleIdTracker,
    private val hostedSdkVersionInfo: HostedSdkVersionInfo,
    private val logger: EmbLogger,
) : EmbraceInternalInterface by impl, ReactNativeInternalInterface {

    override fun logUnhandledJsException(
        name: String,
        message: String,
        type: String?,
        stacktrace: String?,
    ) {
        if (embrace.isStarted) {
            val exception = JsException(name, message, type, stacktrace)
            crashService.logUnhandledJsException(exception)
        } else {
            logger.logSdkNotInitialized("log JS exception")
        }
    }

    override fun logHandledJsException(
        name: String,
        message: String,
        properties: Map<String, Any>,
        stacktrace: String?,
    ) {
        if (embrace.isStarted) {
            embrace.logMessage(
                Severity.ERROR,
                message,
                properties,
                null,
                stacktrace,
                LogExceptionType.HANDLED,
                null,
                null
            )
        } else {
            logger.logSdkNotInitialized("log JS exception")
        }
    }

    override fun setJavaScriptPatchNumber(number: String?) {
        if (embrace.isStarted) {
            if (number.isNullOrEmpty()) {
                return
            }
            hostedSdkVersionInfo.javaScriptPatchNumber = number
        } else {
            logger.logSdkNotInitialized("set JavaScript patch number")
        }
    }

    override fun setReactNativeSdkVersion(version: String?) {
        if (embrace.isStarted) {
            hostedSdkVersionInfo.hostedSdkVersion = version
        } else {
            logger.logSdkNotInitialized("set React Native SDK version")
        }
    }

    override fun setReactNativeVersionNumber(version: String?) {
        if (embrace.isStarted) {
            if (version.isNullOrEmpty()) {
                return
            }
            hostedSdkVersionInfo.hostedPlatformVersion = version
        } else {
            logger.logSdkNotInitialized("set React Native version number")
        }
    }

    override fun setJavaScriptBundleUrl(context: Context, url: String) {
        setJavaScriptBundleUrl(url, null)
    }

    override fun setCacheableJavaScriptBundleUrl(context: Context, url: String, didUpdate: Boolean) {
        setJavaScriptBundleUrl(url, didUpdate)
    }

    private fun setJavaScriptBundleUrl(url: String, didUpdate: Boolean? = null) {
        if (embrace.isStarted) {
            rnBundleIdTracker.setReactNativeBundleId(url, didUpdate)
        } else {
            logger.logSdkNotInitialized("set JavaScript bundle URL")
        }
    }

    override fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String,
    ) {
        embrace.logRnAction(name, startTime, endTime, properties, bytesSent, output)
    }

    override fun logRnView(screen: String) {
        embrace.logRnView(screen)
    }
}
