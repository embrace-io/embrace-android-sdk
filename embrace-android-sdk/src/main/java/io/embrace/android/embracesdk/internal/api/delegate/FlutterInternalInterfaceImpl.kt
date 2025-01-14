package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.FlutterException.embFlutterExceptionContext
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.FlutterException.embFlutterExceptionLibrary
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.opentelemetry.api.common.AttributeKey

internal class FlutterInternalInterfaceImpl(
    private val embrace: EmbraceImpl,
    private val impl: EmbraceInternalInterface,
    private val hostedSdkVersionInfo: HostedSdkVersionInfo,
    private val logger: EmbLogger,
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
        library: String?,
    ) {
        logDartException(stack, name, message, context, library, LogExceptionType.HANDLED)
    }

    override fun logUnhandledDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?,
    ) {
        logDartException(stack, name, message, context, library, LogExceptionType.UNHANDLED)
    }

    private fun logDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?,
        exceptionType: LogExceptionType,
    ) {
        if (embrace.isStarted) {
            val attrs = mutableMapOf<AttributeKey<String>, String>()
            context?.let { attrs[embFlutterExceptionContext.attributeKey] = it }
            library?.let { attrs[embFlutterExceptionLibrary.attributeKey] = it }

            embrace.logMessage(
                severity = Severity.ERROR,
                message = "Dart error",
                customStackTrace = stack,
                logExceptionType = exceptionType,
                exceptionName = name,
                exceptionMessage = message,
                customLogAttrs = attrs
            )
        } else {
            logger.logSdkNotInitialized("logDartError")
        }
    }
}
