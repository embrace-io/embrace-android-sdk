package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

internal class FlutterInternalInterfaceImpl(
    private val embrace: EmbraceImpl,
    private val impl: EmbraceInternalInterface,
    private val metadataService: MetadataService,
    private val logger: InternalEmbraceLogger
) : EmbraceInternalInterface by impl, FlutterInternalInterface {

    override fun setEmbraceFlutterSdkVersion(version: String?) {
        if (embrace.isStarted) {
            if (version != null) {
                metadataService.setEmbraceFlutterSdkVersion(version)
            }
        } else {
            logger.logSDKNotInitialized("setEmbraceFlutterSdkVersion")
        }
    }

    override fun setDartVersion(version: String?) {
        if (embrace.isStarted) {
            if (version != null) {
                metadataService.setDartVersion(version)
            }
        } else {
            logger.logSDKNotInitialized("setDartVersion")
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
            logger.logSDKNotInitialized("logDartError")
        }
    }
}
