package io.embrace.android.embracesdk.internal.worker

import java.util.concurrent.Callable
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
public class ScheduledWorker(
    private val impl: ScheduledExecutorService
) {

    /**
     * Schedules a task for future execution and returns a [ScheduledFuture].
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> schedule(
        command: Runnable?,
        delay: Long,
        unit: TimeUnit?
    ): ScheduledFuture<T> {
        return impl.schedule(command, delay, unit) as ScheduledFuture<T>
    }

    /**
     * Schedules a task for recurring execution and returns a [ScheduledFuture].
     */
    public fun scheduleWithFixedDelay(
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
    public fun submit(runnable: Runnable): Future<*> = impl.submit(runnable)

    /**
     * Submits a task for execution and returns a [Future].
     */
    public fun <T> submit(callable: Callable<T>): Future<T> = impl.submit(callable)

    @Deprecated(
        "Use scheduleWithFixedDelay instead.",
    )
    public fun scheduleAtFixedRate(
        runnable: Runnable,
        initialDelay: Long,
        intervalMs: Long,
        unit: TimeUnit
    ): ScheduledFuture<*> = impl.scheduleAtFixedRate(runnable, initialDelay, intervalMs, unit)
}
