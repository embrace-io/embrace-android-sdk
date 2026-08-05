package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.payload.Log

/**
 * A service that stores exported logs and provides access to them so they
 * can be sent off-device at the appropriate cadence.
 */
interface LogSink {

    /**
     * Store [Log] objects to be sent in the nexdt batch. Implementations must support concurrent invocations.
     */
    fun storeLogs(logs: List<Log>): StoreDataResult

    /**
     * Returns a snapshot of the currently stored [Log] objects waiting to be sent in the next batch.
     */
    fun logsForNextBatch(): List<Log>

    /**
     * Returns the number of stored [Log] objects waiting to be sent in the next batch.
     */
    fun storedLogCount(): Int

    /**
     * Removes and returns the stored [Log] objects to be sent in the next batch, in the order they were stored, up
     * to an implementation-defined maximum batch size.
     */
    fun flushBatch(): List<Log>

    /**
     * Return a [Log] that is to be delivered in its own request
     */
    fun pollUnbatchedLog(): LogRequest<Log>?

    /**
     * Registers a callback to be called after new logs are stored.
     */
    fun registerLogStoredCallback(onLogsStored: () -> Unit)
}
