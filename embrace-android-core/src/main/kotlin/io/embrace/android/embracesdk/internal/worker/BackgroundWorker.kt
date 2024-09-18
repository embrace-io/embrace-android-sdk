package io.embrace.android.embracesdk.internal.worker

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Submits tasks to a background thread pool.
 *
 * This class is necessary because it hides aspects of the ExecutorService API that we don't want
 * to expose as part of the internal API.
 */
class BackgroundWorker(
    private val impl: ExecutorService
) {

    /**
     * Submits a task for execution and returns a [Future].
     */
    fun submit(
        priority: TaskPriority = TaskPriority.NORMAL,
        runnable: Runnable
    ): Future<*> {
        return impl.submit(PriorityRunnable(priority, runnable))
    }

    /**
     * Submits a task for execution and returns a [Future].
     */
    fun <T> submit(
        priority: TaskPriority = TaskPriority.NORMAL,
        callable: Callable<T>
    ): Future<T> {
        return impl.submit(PriorityCallable(priority, callable))
    }

    /**
     * Shutdown the worker. If [timeoutMs] is greater than 0, the worker will
     * block for the specified milliseconds if tasks are still enqueued or running.
     */
    fun shutdownAndWait(timeoutMs: Long = 0) {
        runCatching {
            with(impl) {
                shutdown()
                awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)
            }
        }
    }
}
