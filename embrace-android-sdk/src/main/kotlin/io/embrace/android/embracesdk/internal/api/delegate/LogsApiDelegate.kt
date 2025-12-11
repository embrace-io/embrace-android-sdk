package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.arch.attrs.embExceptionHandling
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.instrumentation.fcm.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.internal.instrumentation.fcm.fcmDataSource
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment.EmbraceHosted
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.ATTACHMENT_TOO_LARGE
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.OVER_MAX_ATTACHMENTS
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.UNKNOWN
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes

internal class LogsApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : LogsApi {

    private val logService by embraceImplInject(sdkCallChecker) { bootstrapper.logModule.logService }
    private val attachmentService by embraceImplInject(sdkCallChecker) {
        bootstrapper.logModule.attachmentService
    }
    private val logger by embraceImplInject(sdkCallChecker) {
        bootstrapper.initModule.logger
    }
    private val payloadStore by embraceImplInject(sdkCallChecker) {
        bootstrapper.deliveryModule.payloadStore
    }

    override fun logInfo(message: String) = logMessage(message, Severity.INFO)
    override fun logWarning(message: String) = logMessage(message, Severity.WARNING)
    override fun logError(message: String) = logMessage(message, Severity.ERROR)

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>,
    ) {
        logMessageImpl(
            severity = severity,
            message = message,
            attributes = properties,
        )
    }

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>,
        attachment: ByteArray,
    ) {
        val obj = attachmentService?.createAttachment(attachment) ?: return
        logAttachmentErrorIfNeeded(obj)
        logMessageImpl(
            severity = severity,
            message = message,
            attributes = properties,
            attachment = obj,
        )
    }

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>,
        attachmentId: String,
        attachmentUrl: String,
    ) {
        val obj = attachmentService?.createAttachment(attachmentId, attachmentUrl) ?: return
        logAttachmentErrorIfNeeded(obj)
        logMessageImpl(
            severity = severity,
            message = message,
            attributes = properties,
            attachment = obj,
        )
    }

    private fun logAttachmentErrorIfNeeded(obj: Attachment) {
        val msg = when (obj.errorCode) {
            ATTACHMENT_TOO_LARGE -> "Supplied attachment exceeds 1Mb limit. This attachment will not be uploaded."
            OVER_MAX_ATTACHMENTS -> "A maximum of 5 attachments are allowed per session. This attachment will not be uploaded."
            UNKNOWN -> "An unknown error occurred while processing the attachment."
            else -> return
        }
        logger?.logError(msg, RuntimeException(msg))
    }

    override fun logException(
        throwable: Throwable,
        severity: Severity,
        properties: Map<String, Any>,
        message: String?,
    ) {
        val exceptionMessage = throwable.message ?: ""
        logMessageImpl(
            severity = severity,
            message = message ?: exceptionMessage,
            attributes = properties,
            stackTraceElements = throwable.getSafeStackTrace(),
            logExceptionType = LogExceptionType.HANDLED,
            exceptionName = throwable.javaClass.simpleName,
            exceptionMessage = exceptionMessage,
        )
    }

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>,
        message: String?,
    ) {
        logMessageImpl(
            severity = severity,
            message = message ?: "",
            attributes = properties,
            stackTraceElements = stacktraceElements,
            logExceptionType = LogExceptionType.HANDLED,
            exceptionMessage = message,
        )
    }

    fun logMessageImpl(
        severity: Severity,
        message: String,
        attributes: Map<String, Any> = emptyMap(),
        stackTraceElements: Array<StackTraceElement>? = null,
        customStackTrace: String? = null,
        logExceptionType: LogExceptionType = LogExceptionType.NONE,
        exceptionName: String? = null,
        exceptionMessage: String? = null,
        attachment: Attachment? = null,
    ) {
        if (sdkCallChecker.check("log_message")) {
            runCatching {
                val dst = logService ?: return
                val attrs = attributes.toMutableMap()

                // add exception name + message attrs
                exceptionName?.let { attrs[ExceptionAttributes.EXCEPTION_TYPE] = it }
                exceptionMessage?.let { attrs[ExceptionAttributes.EXCEPTION_MESSAGE] = it }

                // add attachment attrs
                if (attachment != null) {
                    attrs.putAll(attachment.attributes.mapKeys { it.key.name })
                }

                // map severity value
                val logSeverity = when (severity) {
                    Severity.INFO -> LogSeverity.INFO
                    Severity.WARNING -> LogSeverity.WARNING
                    Severity.ERROR -> LogSeverity.ERROR
                }

                // add log type
                if (logExceptionType != LogExceptionType.NONE) {
                    attrs[embExceptionHandling.name] = logExceptionType.value
                }

                // send log
                dst.log(
                    message = message,
                    severity = logSeverity,
                    logExceptionType = logExceptionType,
                    attributes = attrs,
                    stackTraceElements = stackTraceElements,
                    customStackTrace = customStackTrace,
                )

                // store attachment
                if (attachment is EmbraceHosted && attachment.shouldAttemptUpload()) {
                    val envelope = Envelope(data = Pair(attachment.id, attachment.bytes))
                    payloadStore?.storeAttachment(envelope)
                }
            }
        }
    }

    @Deprecated("This API is deprecated and will be removed in a future release.Use logMessage() instead.")
    override fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int?,
        isNotification: Boolean?,
        hasData: Boolean?,
    ) {
        if (sdkCallChecker.check("log_push_notification")) {
            if (hasData == null || isNotification == null || messageDeliveredPriority == null) {
                return
            }
            val type = PushNotificationBreadcrumb.NotificationType.notificationTypeFor(hasData, isNotification)
            fcmDataSource?.logPushNotification(title, body, topic, id, notificationPriority, type)
        }
    }
}
