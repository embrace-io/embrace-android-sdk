package io.embrace.android.embracesdk.internal.session.orchestrator

/**
 * Buffers telemetry in order before it is written to disk.
 */
internal interface TelemetryQueue<T> {

    /**
     * The number of records currently buffered.
     */
    val size: Int

    /**
     * Buffers [item].
     */
    fun add(item: T)

    /**
     * Buffers [items].
     */
    fun add(items: List<T>)

    /**
     * Removes any instances of [item] that are in the queue.
     */
    fun remove(item: T)

    /**
     * Returns every telemetry object that should be written after removing them from the queue.
     */
    fun drain(): List<T>
}
