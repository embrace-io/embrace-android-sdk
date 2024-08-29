package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import io.embrace.android.embracesdk.internal.worker.WorkerName
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

/**
 * A set of shared executors to be used throughout the SDK
 */
public interface WorkerThreadModule : Closeable {

    /**
     * Return a [BackgroundWorker] matching the [workerName]
     */
    public fun backgroundWorker(workerName: WorkerName): BackgroundWorker

    /**
     * Return the [ScheduledWorker] given the [workerName]
     */
    public fun scheduledWorker(workerName: WorkerName): ScheduledWorker

    /**
     * Returns the thread that monitors the main thread for ANRs
     */
    public val anrMonitorThread: AtomicReference<Thread>

    /**
     * This should only be invoked when the SDK is shutting down. Closing all the worker threads in production means the
     * SDK will not be functional afterwards.
     */
    override fun close()
}
