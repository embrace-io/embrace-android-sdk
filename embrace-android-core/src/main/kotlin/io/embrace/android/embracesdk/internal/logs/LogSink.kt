package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.payload.Log
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData

/**
 * A service that stores exported logs and provides access to them so they
 * can be sent off-device at the appropriate cadence.
 */
public interface LogSink {

    /**
     * Store [Log] objects to be sent in the nexdt batch. Implementations must support concurrent invocations.
     */
    public fun storeLogs(logs: List<LogRecordData>): CompletableResultCode

    /**
     * Returns the list of currently stored [Log] objects, waiting to be sent in the next batch
     */
    public fun completedLogs(): List<Log>

    /**
     * Returns and clears the currently stored [Log] objects, to be used when the next batch is to be sent.
     * Implementations of this method must make sure the clearing and returning is atomic, i.e. logs cannot be added during this operation.
     */
    public fun flushLogs(): List<Log>

    /**
     * Return a [Log] that is to be delivered in its own request
     */
    public fun pollNonbatchedLog(): LogRequest<Log>?

    /**
     * Registers a callback to be called after new logs are stored.
     */
    public fun registerLogStoredCallback(onLogsStored: () -> Unit)
}
