package io.embrace.android.embracesdk.worker

import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Submits scheduled tasks to a background thread pool.
 *
 * This class is necessary because it hides aspects of the ExecutorService API that we don't want
 * to expose as part of the internal API.
 */
internal class ScheduledWorker(
    private val impl: ScheduledExecutorService
) {

    /**
     * Schedules a task for future execution and returns a [ScheduledFuture].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> schedule(
        command: Runnable?,
        delay: Long,
        unit: TimeUnit?
    ): ScheduledFuture<T> {
        return impl.schedule(command, delay, unit) as ScheduledFuture<T>
    }

    /**
     * Schedules a task for recurring execution and returns a [ScheduledFuture].
     */
    fun scheduleWithFixedDelay(
        command: Runnable?,
        initialDelay: Long,
        delay: Long,
        unit: TimeUnit?
    ): ScheduledFuture<*>? {
        return impl.scheduleWithFixedDelay(command, initialDelay, delay, unit)
    }

    /**
     * Submits a task for execution and returns a [Future].
     */
    fun submit(runnable: Runnable): Future<*> = impl.submit(runnable)
}
