package io.embrace.android.embracesdk.internal.api.delegate

import android.content.Context
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.ReactNativeInternalInterface
import io.embrace.android.embracesdk.capture.crash.CrashService
import io.embrace.android.embracesdk.capture.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.JsException

internal class ReactNativeInternalInterfaceImpl(
    private val embrace: EmbraceImpl,
    private val impl: EmbraceInternalInterface,
    private val framework: AppFramework,
    private val crashService: CrashService,
    private val metadataService: MetadataService,
    private val hostedSdkVersionInfo: HostedSdkVersionInfo,
    private val logger: EmbLogger
) : EmbraceInternalInterface by impl, ReactNativeInternalInterface {

    override fun logUnhandledJsException(
        name: String,
        message: String,
        type: String?,
        stacktrace: String?
    ) {
        if (embrace.isStarted()) {
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
        stacktrace: String?
    ) {
        if (embrace.isStarted()) {
            embrace.logMessage(
                EventType.ERROR_LOG,
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
        if (embrace.isStarted()) {
            if (number == null) {
                logger.logError("JavaScript patch number must not be null")
                return
            }
            if (number.isEmpty()) {
                logger.logError("JavaScript patch number must have non-zero length")
                return
            }
            hostedSdkVersionInfo.javaScriptPatchNumber = number
        } else {
            logger.logSdkNotInitialized("set JavaScript patch number")
        }
    }

    override fun setReactNativeSdkVersion(version: String?) {
        if (embrace.isStarted()) {
            hostedSdkVersionInfo.hostedSdkVersion = version
        } else {
            logger.logSdkNotInitialized("set React Native SDK version")
        }
    }

    override fun setReactNativeVersionNumber(version: String?) {
        if (embrace.isStarted()) {
            if (version == null) {
                logger.logError("ReactNative version must not be null")
                return
            }
            if (version.isEmpty()) {
                logger.logError("ReactNative version must have non-zero length")
                return
            }
            hostedSdkVersionInfo.hostedPlatformVersion = version
        } else {
            logger.logSdkNotInitialized("set React Native version number")
        }
    }

    override fun setJavaScriptBundleUrl(context: Context, url: String) {
        setJavaScriptBundleUrl(context, url, null)
    }

    override fun setCacheableJavaScriptBundleUrl(context: Context, url: String, didUpdate: Boolean) {
        setJavaScriptBundleUrl(context, url, didUpdate)
    }

    private fun setJavaScriptBundleUrl(context: Context, url: String, didUpdate: Boolean? = null) {
        if (embrace.isStarted()) {
            if (framework != AppFramework.REACT_NATIVE) {
                logger.logError(
                    "Failed to set Java Script bundle ID URL. Current framework: " +
                        framework.name + " is not React Native."
                )
                return
            }
            metadataService.setReactNativeBundleId(context, url, didUpdate)
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
        output: String
    ) {
        embrace.logRnAction(name, startTime, endTime, properties, bytesSent, output)
    }

    override fun logRnView(screen: String) {
        embrace.logRnView(screen)
    }
}
