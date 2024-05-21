package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.Severity

/**
 * The public API that is used to send log messages.
 */
internal interface LogsApi {
    /**
     * Remotely logs a message at the given severity level. These log messages will appear as part of the session
     * timeline, and can be used to describe what was happening at a particular time within the app.
     *
     * @param message  the message to remotely log
     * @param severity the severity level of the log message
     */
    fun logMessage(
        message: String,
        severity: Severity,
    )

    /**
     * Remotely logs a message at the given severity level. These log messages will appear as part of the session
     * timeline, and can be used to describe what was happening at a particular time within the app.
     *
     * @param message    the message to remotely log
     * @param severity   the severity level of the log message
     * @param properties the properties to attach to the log message
     */
    fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?,
    )

    /**
     * Remotely logs a message at INFO level. These log messages will appear as part of the session
     * timeline, and can be used to describe what was happening at a particular time within the app.
     *
     * @param message the message to remotely log
     */
    fun logInfo(message: String)

    /**
     * Remotely logs a message at WARN level. These log messages will appear as part of the session
     * timeline, and can be used to describe what was happening at a particular time within the app.
     *
     * @param message the message to remotely log
     */
    fun logWarning(message: String)

    /**
     * Remotely logs a message at ERROR level. These log messages will appear as part of the session
     * timeline, and can be used to describe what was happening at a particular time within the app.
     *
     * @param message the message to remotely log
     */
    fun logError(message: String)

    /**
     * Remotely logs a Throwable/Exception at ERROR level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param throwable the throwable to remotely log
     */
    fun logException(throwable: Throwable)

    /**
     * Remotely logs a Throwable/Exception at given severity level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param throwable the throwable to remotely log
     * @param severity  the severity level of the log message
     */
    fun logException(
        throwable: Throwable,
        severity: Severity,
    )

    /**
     * Remotely logs a Throwable/Exception at given severity level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param throwable  the throwable to remotely log
     * @param severity   the severity level of the log message
     * @param properties custom key-value pairs to include with the log message
     */
    fun logException(
        throwable: Throwable,
        severity: Severity,
        properties: Map<String, Any>?,
    )

    /**
     * Remotely logs a Throwable/Exception at given severity level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param throwable       the throwable to remotely log
     * @param severity        the severity level of the log message
     * @param properties      custom key-value pairs to include with the log message
     * @param message         the message to remotely log instead of the throwable message
     */
    fun logException(
        throwable: Throwable,
        severity: Severity,
        properties: Map<String, Any>?,
        message: String?,
    )

    /**
     * Remotely logs a custom stacktrace at ERROR level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param stacktraceElements the stacktrace to remotely log
     */
    fun logCustomStacktrace(stacktraceElements: Array<StackTraceElement>)

    /**
     * Remotely logs a custom stacktrace at given severity level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param stacktraceElements the stacktrace to remotely log
     * @param severity           the severity level of the log message
     */
    fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
    )

    /**
     * Remotely logs a custom stacktrace at given severity level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param stacktraceElements the stacktrace to remotely log
     * @param severity           the severity level of the log message
     * @param properties         custom key-value pairs to include with the log message
     */
    fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>?,
    )

    /**
     * Remotely logs a custom stacktrace at given severity level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param stacktraceElements the stacktrace to remotely log
     * @param severity           the severity level of the log message
     * @param properties         custom key-value pairs to include with the log message
     * @param message            the message to remotely log instead of the throwable message
     */
    fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>?,
        message: String?,
    )

    /**
     * Saves captured push notification information into session payload
     *
     * @param title                    the title of the notification as a string (or null)
     * @param body                     the body of the notification as a string (or null)
     * @param topic                    the notification topic (if a user subscribed to one), or null
     * @param id                       A unique ID identifying the message
     * @param notificationPriority     the notificationPriority of the message (as resolved on the device)
     * @param messageDeliveredPriority the priority of the message (as resolved on the server)
     * @param isNotification           if it is a notification message.
     * @param hasData                  if the message contains payload data.
     */
    fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int?,
        isNotification: Boolean?,
        hasData: Boolean?,
    )
}
