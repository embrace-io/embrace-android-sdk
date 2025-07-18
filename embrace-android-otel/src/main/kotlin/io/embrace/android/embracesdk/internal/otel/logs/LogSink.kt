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
     * Returns the list of currently stored [Log] objects, waiting to be sent in the next batch
     */
    fun logsForNextBatch(): List<Log>

    /**
     * Returns and clears the currently stored [Log] objects, to be used when the next batch is to be sent.
     * Implementations of this method must make sure the clearing and returning is atomic, i.e. logs cannot be added during this operation.
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
