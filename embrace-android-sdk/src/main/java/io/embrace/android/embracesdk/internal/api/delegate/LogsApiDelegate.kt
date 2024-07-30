package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.internal.payload.EventType.ERROR_LOG
import io.embrace.android.embracesdk.internal.payload.EventType.INFO_LOG
import io.embrace.android.embracesdk.internal.payload.EventType.WARNING_LOG
import io.embrace.android.embracesdk.internal.payload.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.internal.utils.PropertyUtils.normalizeProperties
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace

internal class LogsApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : LogsApi {

    private val logger = bootstrapper.initModule.logger
    private val logService by embraceImplInject(sdkCallChecker) { bootstrapper.customerLogModule.logService }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) { bootstrapper.sessionModule.sessionOrchestrator }
    private val pushNotificationService by embraceImplInject(sdkCallChecker) {
        bootstrapper.dataCaptureServiceModule.pushNotificationService
    }

    override fun logInfo(message: String) {
        logMessage(message, Severity.INFO)
    }

    override fun logWarning(message: String) {
        logMessage(message, Severity.WARNING)
    }

    override fun logError(message: String) {
        logMessage(message, Severity.ERROR)
    }

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
        properties: Map<String, Any>?
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
        properties: Map<String, Any>?
    ) {
        logCustomStacktrace(stacktraceElements, severity, properties, null)
    }

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?
    ) {
        logMessage(
            fromSeverity(severity),
            message,
            properties,
            null,
            null,
            LogExceptionType.NONE,
            null,
            null
        )
    }

    override fun logException(
        throwable: Throwable,
        severity: Severity,
        properties: Map<String, Any>?,
        message: String?
    ) {
        val exceptionMessage = if (throwable.message != null) throwable.message else ""
        logMessage(
            fromSeverity(severity),
            (
                message
                    ?: exceptionMessage
                ) ?: "",
            properties,
            throwable.getSafeStackTrace(),
            null,
            LogExceptionType.HANDLED,
            null,
            null,
            throwable.javaClass.simpleName,
            exceptionMessage
        )
    }

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>?,
        message: String?
    ) {
        logMessage(
            fromSeverity(severity),
            message
                ?: "",
            properties,
            stacktraceElements,
            null,
            LogExceptionType.HANDLED,
            null,
            null,
            null,
            message
        )
    }

    @JvmOverloads
    fun logMessage(
        type: EventType,
        message: String,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        logExceptionType: LogExceptionType,
        context: String?,
        library: String?,
        exceptionName: String? = null,
        exceptionMessage: String? = null
    ) {
        if (sdkCallChecker.check("log_message")) {
            try {
                logService?.log(
                    message,
                    type,
                    logExceptionType,
                    normalizeProperties(properties, logger),
                    stackTraceElements,
                    customStackTrace,
                    context,
                    library,
                    exceptionName,
                    exceptionMessage
                )
                sessionOrchestrator?.reportBackgroundActivityStateChange()
            } catch (ex: Exception) {
                logger.logDebug("Failed to log message using Embrace SDK.", ex)
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
        hasData: Boolean?
    ) {
        if (sdkCallChecker.check("log_push_notification")) {
            if (hasData == null || isNotification == null || messageDeliveredPriority == null) {
                return
            }
            val type = PushNotificationBreadcrumb.NotificationType.notificationTypeFor(hasData, isNotification)
            pushNotificationService?.logPushNotification(title, body, topic, id, notificationPriority, messageDeliveredPriority, type)
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }

    private fun fromSeverity(severity: Severity): EventType = when (severity) {
        Severity.INFO -> INFO_LOG
        Severity.WARNING -> WARNING_LOG
        Severity.ERROR -> ERROR_LOG
    }
}
