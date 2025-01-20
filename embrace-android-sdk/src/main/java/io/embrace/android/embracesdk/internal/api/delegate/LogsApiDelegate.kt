package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment.EmbraceHosted
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.ATTACHMENT_TOO_LARGE
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.OVER_MAX_ATTACHMENTS
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.UNKNOWN
import io.embrace.android.embracesdk.internal.payload.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.semconv.ExceptionAttributes

internal class LogsApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : LogsApi {

    private val logService by embraceImplInject(sdkCallChecker) { bootstrapper.logModule.logService }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) {
        bootstrapper.sessionOrchestrationModule.sessionOrchestrator
    }
    private val pushNotificationDataSource by embraceImplInject(sdkCallChecker) {
        bootstrapper.featureModule.pushNotificationDataSource.dataSource
    }
    private val serializer by embraceImplInject(sdkCallChecker) {
        bootstrapper.initModule.jsonSerializer
    }
    private val attachmentService by embraceImplInject(sdkCallChecker) {
        bootstrapper.logModule.attachmentService
    }
    private val logger by embraceImplInject(sdkCallChecker) {
        bootstrapper.initModule.logger
    }

    override fun logInfo(message: String) = logMessage(message, Severity.INFO)
    override fun logWarning(message: String) = logMessage(message, Severity.WARNING)
    override fun logError(message: String) = logMessage(message, Severity.ERROR)

    override fun logMessage(message: String, severity: Severity) {
        logMessage(message, severity, null)
    }

    override fun logException(throwable: Throwable) {
        logException(throwable, Severity.ERROR)
    }

    override fun logException(throwable: Throwable, severity: Severity) {
        logException(throwable, severity, null)
    }

    override fun logException(
        throwable: Throwable,
        severity: Severity,
        properties: Map<String, Any>?,
    ) {
        logException(throwable, severity, properties, null)
    }

    override fun logCustomStacktrace(stacktraceElements: Array<StackTraceElement>) {
        logCustomStacktrace(stacktraceElements, Severity.ERROR)
    }

    override fun logCustomStacktrace(stacktraceElements: Array<StackTraceElement>, severity: Severity) {
        logCustomStacktrace(stacktraceElements, severity, null)
    }

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>?,
    ) {
        logCustomStacktrace(stacktraceElements, severity, properties, null)
    }

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?,
    ) {
        logMessageImpl(
            severity = severity,
            message = message,
            properties = properties,
        )
    }

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?,
        attachment: ByteArray,
    ) {
        val obj = attachmentService?.createAttachment(attachment) ?: return
        logAttachmentErrorIfNeeded(obj)
        logMessageImpl(
            severity = severity,
            message = message,
            properties = properties,
            attachment = obj,
        )
    }

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?,
        attachmentId: String,
        attachmentUrl: String,
    ) {
        val obj = attachmentService?.createAttachment(attachmentId, attachmentUrl) ?: return
        logAttachmentErrorIfNeeded(obj)
        logMessageImpl(
            severity = severity,
            message = message,
            properties = properties,
            attachment = obj,
        )
    }

    private fun logAttachmentErrorIfNeeded(obj: Attachment) {
        if (obj.errorCode != null) {
            val msg = when (obj.errorCode) {
                ATTACHMENT_TOO_LARGE -> "Supplied attachment exceeds 1Mb limit. This attachment will not be uploaded."
                OVER_MAX_ATTACHMENTS -> "A maximum of 5 attachments are allowed per session. This attachment will not be uploaded."
                UNKNOWN -> "An unknown error occurred while processing the attachment."
                null -> null
            } ?: return
            logger?.logError(msg, RuntimeException(msg))
        }
    }

    override fun logException(
        throwable: Throwable,
        severity: Severity,
        properties: Map<String, Any>?,
        message: String?,
    ) {
        val exceptionMessage = throwable.message ?: ""
        logMessageImpl(
            severity = severity,
            message = message ?: exceptionMessage,
            properties = properties,
            stackTraceElements = throwable.getSafeStackTrace(),
            logExceptionType = LogExceptionType.HANDLED,
            exceptionName = throwable.javaClass.simpleName,
            exceptionMessage = exceptionMessage,
        )
    }

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>?,
        message: String?,
    ) {
        logMessageImpl(
            severity = severity,
            message = message ?: "",
            properties = properties,
            stackTraceElements = stacktraceElements,
            logExceptionType = LogExceptionType.HANDLED,
            exceptionMessage = message,
        )
    }

    @JvmOverloads
    fun logMessageImpl(
        severity: Severity,
        message: String,
        properties: Map<String, Any>? = null,
        stackTraceElements: Array<StackTraceElement>? = null,
        customStackTrace: String? = null,
        logExceptionType: LogExceptionType = LogExceptionType.NONE,
        exceptionName: String? = null,
        exceptionMessage: String? = null,
        customLogAttrs: Map<AttributeKey<String>, String> = emptyMap(),
        attachment: Attachment? = null,
    ) {
        if (sdkCallChecker.check("log_message")) {
            runCatching {
                val attrs = mutableMapOf<AttributeKey<String>, String>()
                exceptionName?.let { attrs[ExceptionAttributes.EXCEPTION_TYPE] = it }
                exceptionMessage?.let { attrs[ExceptionAttributes.EXCEPTION_MESSAGE] = it }

                val stacktrace =
                    stackTraceElements?.let(checkNotNull(serializer)::truncatedStacktrace) ?: customStackTrace
                stacktrace?.let { attrs[ExceptionAttributes.EXCEPTION_STACKTRACE] = it }

                if (attachment != null) {
                    attrs.putAll(attachment.attributes.mapKeys { it.key.attributeKey })
                }

                val logAttachment = when {
                    attachment is EmbraceHosted && attachment.shouldAttemptUpload() -> attachment
                    else -> null
                }
                logService?.log(
                    message,
                    severity,
                    logExceptionType,
                    properties,
                    attrs.plus(customLogAttrs),
                    logAttachment
                )
                sessionOrchestrator?.reportBackgroundActivityStateChange()
            }
        }
    }

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
            pushNotificationDataSource?.logPushNotification(title, body, topic, id, notificationPriority, type)
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }
}
