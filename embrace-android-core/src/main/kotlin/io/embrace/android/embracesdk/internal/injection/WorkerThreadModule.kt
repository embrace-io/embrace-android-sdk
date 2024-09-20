package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PrioritizedWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

/**
 * A set of shared executors to be used throughout the SDK
 */
interface WorkerThreadModule : Closeable {

    /**
     * Return a [BackgroundWorker] matching the [worker]
     */
    fun backgroundWorker(worker: Worker): BackgroundWorker

    /**
     * Return a [PrioritizedWorker] matching the [worker]
     */
    fun prioritizedWorker(worker: Worker): PrioritizedWorker

    /**
     * Returns the thread that monitors the main thread for ANRs
     */
    val anrMonitorThread: AtomicReference<Thread>

    /**
     * This should only be invoked when the SDK is shutting down. Closing all the worker threads in production means the
     * SDK will not be functional afterwards.
     */
    override fun close()
}
