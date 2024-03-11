package io.embrace.android.embracesdk.internal.logs

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData

/**
 * A service that stores exported logs and provides access to them so they
 * can be sent off-device at the appropriate cadence.
 */
internal interface LogSink {

    /**
     * Stores Logs. Implementations must support concurrent invocations.
     */
    fun storeLogs(logs: List<LogRecordData>): CompletableResultCode

    /**
     * Returns the list of currently stored Logs.
     */
    fun completedLogs(): List<EmbraceLogRecordData>

    /**
     * Returns and clears the currently stored Logs. Implementations of this method must make sure the clearing and returning is
     * atomic, i.e. logs cannot be added during this operation.
     * @param max The maximum number of logs to flush. If null, all logs are flushed.
     */
    fun flushLogs(max: Int? = null): List<EmbraceLogRecordData>

    /**
     * Registers a callback to be called when logs are stored.
     */
    fun callOnLogsStored(onLogsStored: () -> Unit)
}
