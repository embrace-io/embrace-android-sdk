package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.logging.EmbLogger

internal class FlutterInternalInterfaceImpl(
    private val embrace: EmbraceImpl,
    private val impl: EmbraceInternalInterface,
    private val hostedSdkVersionInfo: HostedSdkVersionInfo,
    private val logger: EmbLogger
) : EmbraceInternalInterface by impl, FlutterInternalInterface {

    override fun setEmbraceFlutterSdkVersion(version: String?) {
        if (embrace.isStarted) {
            if (version != null) {
                hostedSdkVersionInfo.hostedSdkVersion = version
            }
        } else {
            logger.logSdkNotInitialized("setEmbraceFlutterSdkVersion")
        }
    }

    override fun setDartVersion(version: String?) {
        if (embrace.isStarted) {
            if (version != null) {
                hostedSdkVersionInfo.hostedPlatformVersion = version
            }
        } else {
            logger.logSdkNotInitialized("setDartVersion")
        }
    }

    override fun logHandledDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?
    ) {
        logDartException(stack, name, message, context, library, LogExceptionType.HANDLED)
    }

    override fun logUnhandledDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?
    ) {
        logDartException(stack, name, message, context, library, LogExceptionType.UNHANDLED)
    }

    private fun logDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?,
        exceptionType: LogExceptionType
    ) {
        if (embrace.isStarted) {
            embrace.logMessage(
                EventType.ERROR_LOG,
                "Dart error",
                null,
                null,
                stack,
                exceptionType,
                context,
                library,
                name,
                message
            )
        } else {
            logger.logSdkNotInitialized("logDartError")
        }
    }
}
