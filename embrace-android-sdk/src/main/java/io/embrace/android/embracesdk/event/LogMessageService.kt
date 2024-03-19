package io.embrace.android.embracesdk.event

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.internal.logs.BaseLogService
import io.embrace.android.embracesdk.payload.NetworkCapturedCall

/**
 * Logs messages remotely, so that they can be viewed as events during a user's session.
 */
internal interface LogMessageService : BaseLogService {

    /**
     * Creates a network event.
     *
     * @param networkCaptureCall the captured network information
     */
    fun logNetwork(networkCaptureCall: NetworkCapturedCall?)

    /**
     * Creates a remote log.
     *
     * @param message            the message to log
     * @param type               the type of message to log, which must be INFO_LOG, WARNING_LOG, or ERROR_LOG
     * @param logExceptionType   whether the log is a handled exception, unhandled, or non an exception
     * @param properties         custom properties to send as part of the event
     * @param stackTraceElements the stacktrace elements of a throwable
     * @param customStackTrace   stacktrace string for non-JVM exceptions
     * @param exceptionName      the exception name of a Throwable is it is present
     * @param exceptionMessage   the exception message of a Throwable is it is present
     */
    @Suppress("LongParameterList")
    fun log(
        message: String,
        type: EventType,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        framework: Embrace.AppFramework,
        context: String?,
        library: String?,
        exceptionName: String?,
        exceptionMessage: String?
    )

    /**
     * Finds all IDs of log network events within the given time window.
     *
     * @param startTime the beginning of the time window
     * @param endTime   the end of the time window
     * @return the list of log IDs within the specified range
     */
    fun findNetworkLogIds(startTime: Long, endTime: Long): List<String>
}
