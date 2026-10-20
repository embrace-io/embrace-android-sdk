package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.Exception
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.Log
import io.embrace.android.embracesdk.internal.arch.schema.SendMode
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.instrumentation.fcm.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.internal.instrumentation.fcm.fcmDataSource
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logs.LogExceptionType
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment.EmbraceHosted
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.ATTACHMENT_TOO_LARGE
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.OVER_MAX_ATTACHMENTS
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.UNKNOWN
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.ExceptionAttributes

internal class LogsApiDelegate(
    private val logService: Lazy<LogService>,
    private val attachmentService: AttachmentService,
    private val errorHandler: ErrorHandler,
    private val payloadStore: PayloadStore?,
    private val serializer: PlatformSerializer,
    private val telemetryService: InternalTelemetryService,
) : LogsApi {

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
        val obj = attachmentService.createAttachment(attachment)
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
        val obj = attachmentService.createAttachment(attachmentId, attachmentUrl)
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
        telemetryService.trackAppliedLimit("attachment", AppliedLimitType.DROP)
        errorHandler.log(msg, RuntimeException(msg), severity = ErrorHandler.Severity.ERROR)
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
            exceptionData = ExceptionData(
                name = throwable.javaClass.simpleName,
                message = exceptionMessage,
                stacktrace = throwable.getSafeStackTrace()?.let(::serializeStacktrace),
                logExceptionType = LogExceptionType.HANDLED,
            ),
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
            exceptionData = ExceptionData(
                name = null,
                message = message,
                stacktrace = serializeStacktrace(stacktraceElements),
                logExceptionType = LogExceptionType.HANDLED,
            ),
        )
    }

    fun logMessageImpl(
        severity: Severity,
        message: String,
        attributes: Map<String, Any> = emptyMap(),
        exceptionData: ExceptionData? = null,
        attachment: Attachment? = null,
    ) {
        errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) {
            val dst = logService.value
            sendLog(attributes, exceptionData, attachment, severity, dst, message)
        }
    }

    private fun sendLog(
        attributes: Map<String, Any>,
        exceptionData: ExceptionData?,
        attachment: Attachment?,
        severity: Severity,
        dst: LogService,
        message: String,
    ) {
        val attrs = attributes.toMutableMap()

        // add exception name + message attrs
        val exceptionType = exceptionData?.logExceptionType ?: LogExceptionType.NONE
        exceptionData?.name?.let { attrs[ExceptionAttributes.EXCEPTION_TYPE] = it }
        exceptionData?.message?.let { attrs[ExceptionAttributes.EXCEPTION_MESSAGE] = it }
        exceptionData?.stacktrace?.let { attrs[ExceptionAttributes.EXCEPTION_STACKTRACE] = it }

        if (exceptionType != LogExceptionType.NONE) {
            attrs[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING] = exceptionType.value
        }

        // add attachment attrs
        if (attachment != null) {
            attrs.putAll(attachment.attributes)
        }

        // map severity value
        val logSeverity = when (severity) {
            Severity.INFO -> LogSeverity.INFO
            Severity.WARNING -> LogSeverity.WARNING
            Severity.ERROR -> LogSeverity.ERROR
        }

        // send log
        dst.log(
            message = message,
            severity = logSeverity,
            attributes = attrs,
            schemaProvider = createSchemaProvider(attrs),
        )

        // store attachment
        if (attachment is EmbraceHosted && attachment.shouldAttemptUpload()) {
            val envelope = Envelope(data = Pair(attachment.id, attachment.bytes))
            payloadStore?.storeAttachment(envelope)
        }
    }

    /**
     * Gets a provider for the SchemaType that should be used for the log. Ordinarily this will be [SchemaType.Log] but
     * it's also possible for [SchemaType.Exception] or [SchemaType.Custom] to be supplied.
     */
    private fun createSchemaProvider(attrs: Map<String, Any>): (TelemetryAttributes) -> SchemaType {
        val type = attrs["emb.type"] as? String
        val sendMode = attrs[EmbSessionAttributes.EMB_PRIVATE_SEND_MODE] as? String
        val handledMode = attrs[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING] as? String

        return when {
            type != null -> createCustomSchema(type, sendMode) ?: ::Log
            handledMode == LogExceptionType.UNHANDLED.value -> ::Exception
            handledMode == LogExceptionType.HANDLED.value -> ::Exception
            else -> ::Log
        }
    }

    private fun createCustomSchema(type: String, sendMode: String?): ((TelemetryAttributes) -> SchemaType)? {
        val mode = SendMode.fromString(sendMode)
        val parts = type.split(".")
        if (parts.size != 2) {
            return null
        }
        return { SchemaType.Custom(parts[0], parts[1], it, mode) }
    }

    private fun serializeStacktrace(elements: Array<StackTraceElement>): String? = runCatching {
        serializer.truncatedStacktrace(elements)
    }.getOrNull()

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
        if (hasData == null || isNotification == null || messageDeliveredPriority == null) {
            return
        }
        val type = PushNotificationBreadcrumb.NotificationType.notificationTypeFor(hasData, isNotification)
        fcmDataSource?.logPushNotification(title, body, topic, id, notificationPriority, type)
    }
}
