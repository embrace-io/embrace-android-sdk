package io.embrace.android.embracesdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * The public API that is used to send log messages.
 */
interface LogsApi {

    /**
     * Remotely logs a message at the given severity level. These log messages will appear as part of the session
     * timeline, and can be used to describe what was happening at a particular time within the app.
     *
     * @param message  the message to remotely log
     * @param severity the severity level of the log message
     */
    void logMessage(@NonNull String message,
                    @NonNull Severity severity);

    /**
     * Remotely logs a message at the given severity level. These log messages will appear as part of the session
     * timeline, and can be used to describe what was happening at a particular time within the app.
     *
     * @param message    the message to remotely log
     * @param severity   the severity level of the log message
     * @param properties the properties to attach to the log message
     */
    void logMessage(@NonNull String message,
                    @NonNull Severity severity,
                    @Nullable Map<String, Object> properties);

    /**
     * Remotely logs a message at INFO level. These log messages will appear as part of the session
     * timeline, and can be used to describe what was happening at a particular time within the app.
     *
     * @param message the message to remotely log
     */
    void logInfo(@NonNull String message);

    /**
     * Remotely logs a message at WARN level. These log messages will appear as part of the session
     * timeline, and can be used to describe what was happening at a particular time within the app.
     *
     * @param message the message to remotely log
     */
    void logWarning(@NonNull String message);

    /**
     * Remotely logs a message at ERROR level. These log messages will appear as part of the session
     * timeline, and can be used to describe what was happening at a particular time within the app.
     *
     * @param message the message to remotely log
     */
    void logError(@NonNull String message);

    /**
     * Remotely logs a Throwable/Exception at ERROR level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param throwable the throwable to remotely log
     */
    void logException(@NonNull Throwable throwable);

    /**
     * Remotely logs a Throwable/Exception at given severity level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param throwable the throwable to remotely log
     * @param severity  the severity level of the log message
     */
    void logException(@NonNull Throwable throwable,
                      @NonNull Severity severity);

    /**
     * Remotely logs a Throwable/Exception at given severity level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param throwable  the throwable to remotely log
     * @param severity   the severity level of the log message
     * @param properties custom key-value pairs to include with the log message
     */
    void logException(@NonNull Throwable throwable,
                      @NonNull Severity severity,
                      @Nullable Map<String, Object> properties);

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
    void logException(@NonNull Throwable throwable,
                      @NonNull Severity severity,
                      @Nullable Map<String, Object> properties,
                      @Nullable String message);

    /**
     * Remotely logs a custom stacktrace at ERROR level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param stacktraceElements the stacktrace to remotely log
     */
    void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements);

    /**
     * Remotely logs a custom stacktrace at given severity level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param stacktraceElements the stacktrace to remotely log
     * @param severity           the severity level of the log message
     */
    void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements,
                             @NonNull Severity severity);

    /**
     * Remotely logs a custom stacktrace at given severity level. These log messages and stacktraces
     * will appear as part of the session timeline, and can be used to describe what was happening
     * at a particular time within the app.
     *
     * @param stacktraceElements the stacktrace to remotely log
     * @param severity           the severity level of the log message
     * @param properties         custom key-value pairs to include with the log message
     */
    void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements,
                             @NonNull Severity severity,
                             @Nullable Map<String, Object> properties);

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
    void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements,
                             @NonNull Severity severity,
                             @Nullable Map<String, Object> properties,
                             @Nullable String message);

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
    void logPushNotification(@Nullable String title,
                             @Nullable String body,
                             @Nullable String topic,
                             @Nullable String id,
                             @Nullable Integer notificationPriority,
                             @NonNull Integer messageDeliveredPriority,
                             @NonNull Boolean isNotification,
                             @NonNull Boolean hasData);
}
