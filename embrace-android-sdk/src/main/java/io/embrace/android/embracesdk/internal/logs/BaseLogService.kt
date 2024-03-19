package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.session.MemoryCleanerListener

internal interface BaseLogService : MemoryCleanerListener {
    /**
     * Finds all IDs of log events at info level within the given time window.
     *
     * @param startTime the beginning of the time window
     * @param endTime   the end of the time window
     * @return the list of log IDs within the specified range
     */
    fun findInfoLogIds(startTime: Long, endTime: Long): List<String>

    /**
     * Finds all IDs of log events at warning level within the given time window.
     *
     * @param startTime the beginning of the time window
     * @param endTime   the end of the time window
     * @return the list of log IDs within the specified range
     */
    fun findWarningLogIds(startTime: Long, endTime: Long): List<String>

    /**
     * Finds all IDs of log events at error level within the given time window.
     *
     * @param startTime the beginning of the time window
     * @param endTime   the end of the time window
     * @return the list of log IDs within the specified range
     */
    fun findErrorLogIds(startTime: Long, endTime: Long): List<String>

    /**
     * The total number of info logs that the app attempted to send.
     */
    fun getInfoLogsAttemptedToSend(): Int

    /**
     * The total number of warning logs that the app attempted to send.
     */
    fun getWarnLogsAttemptedToSend(): Int

    /**
     * The total number of error logs that the app attempted to send.
     */
    fun getErrorLogsAttemptedToSend(): Int

    /**
     * The total number of unhandled exceptions sent.
     */
    fun getUnhandledExceptionsSent(): Int
}
