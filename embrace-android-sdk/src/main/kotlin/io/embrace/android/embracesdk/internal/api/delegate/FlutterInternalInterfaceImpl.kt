package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logs.LogExceptionType

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
            val attrs = mutableMapOf(
                "emb.type" to "sys.flutter_exception",
                "emb.private.send_mode" to "immediate",
            )
            context?.let { attrs["emb.exception.context"] = it }
            library?.let { attrs["emb.exception.library"] = it }

            // add exception name + message attrs
            name?.let { attrs["exception.type"] = it }
            message?.let { attrs["exception.message"] = it }
            stack?.let { attrs["exception.stacktrace"] = it }

            if (exceptionType != LogExceptionType.NONE) {
                attrs["emb.exception_handling"] = exceptionType.value
            }

            embrace.logMessage(
                severity = Severity.ERROR,
                message = "Dart error",
                properties = attrs,
            )
        } else {
            logger.logSdkNotInitialized("logDartError")
        }
    }
}
