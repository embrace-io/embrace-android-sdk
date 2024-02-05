package io.embrace.android.embracesdk

import android.content.Context
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.capture.crash.CrashService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.prefs.PreferencesService

internal class ReactNativeInternalInterfaceImpl(
    private val embrace: EmbraceImpl,
    private val impl: EmbraceInternalInterface,
    private val framework: AppFramework,
    private val preferencesService: PreferencesService,
    private val crashService: CrashService,
    private val metadataService: MetadataService,
    private val logger: InternalEmbraceLogger
) : EmbraceInternalInterface by impl, ReactNativeInternalInterface {

    override fun logUnhandledJsException(
        name: String,
        message: String,
        type: String?,
        stacktrace: String?
    ) {
        if (embrace.isStarted) {
            val exception = JsException(name, message, type, stacktrace)
            crashService.logUnhandledJsException(exception)
        } else {
            logger.logSDKNotInitialized("log JS exception")
        }
    }

    override fun logHandledJsException(
        name: String,
        message: String,
        properties: Map<String, Any>,
        stacktrace: String?
    ) {
        if (embrace.isStarted) {
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
            logger.logSDKNotInitialized("log JS exception")
        }
    }

    override fun setJavaScriptPatchNumber(number: String?) {
        if (embrace.isStarted) {
            if (number == null) {
                logger.logError("JavaScript patch number must not be null")
                return
            }
            if (number.isEmpty()) {
                logger.logError("JavaScript patch number must have non-zero length")
                return
            }
            preferencesService.javaScriptPatchNumber = number
        } else {
            logger.logSDKNotInitialized("set JavaScript patch number")
        }
    }

    override fun setReactNativeSdkVersion(version: String?) {
        if (embrace.isStarted) {
            metadataService.setRnSdkVersion(version)
        } else {
            logger.logSDKNotInitialized("set React Native SDK version")
        }
    }

    override fun setReactNativeVersionNumber(version: String?) {
        if (embrace.isStarted) {
            if (version == null) {
                logger.logError("ReactNative version must not be null")
                return
            }
            if (version.isEmpty()) {
                logger.logError("ReactNative version must have non-zero length")
                return
            }
            preferencesService.reactNativeVersionNumber = version
        } else {
            logger.logSDKNotInitialized("set React Native version number")
        }
    }

    override fun setJavaScriptBundleUrl(context: Context, url: String) {
        if (embrace.isStarted) {
            if (framework != AppFramework.REACT_NATIVE) {
                logger.logError(
                    "Failed to set Java Script bundle ID URL. Current framework: " +
                        framework.name + " is not React Native."
                )
                return
            }
            metadataService.setReactNativeBundleId(context, url)
        } else {
            logger.logSDKNotInitialized("set JavaScript bundle URL")
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
