package io.embrace.android.embracesdk.internal.worker

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Submits tasks to a background thread pool.
 *
 * This class is necessary because it hides aspects of the ExecutorService API that we don't want
 * to expose as part of the internal API.
 */
class PriorityWorker<T>(
    private val impl: ExecutorService,
) {

    /**
     * Submits a task for execution and returns a [Future].
     */
    fun submit(
        priorityInfo: T,
        runnable: Runnable,
    ): Future<*> {
        return impl.submit(PriorityRunnable(priorityInfo as Any, runnable))
    }

    /**
     * Submits a task for execution and returns a [Future].
     */
    fun <R> submit(
        priorityInfo: T,
        callable: Callable<R>,
    ): Future<R> {
        return impl.submit(PriorityCallable(priorityInfo as Any, callable))
    }

    /**
     * Cancels [future] and, if cancellation succeeds (i.e. the task hadn't already started
     * running), removes it from the executor's task queue so it doesn't linger there until the
     * executor drains it.
     */
    fun cancelAndRemove(future: Future<*>): Boolean {
        val cancelled = future.cancel(false)
        if (cancelled) {
            (future as? Runnable)?.let { task ->
                (impl as? ThreadPoolExecutor)?.remove(task)
            }
        }
        return cancelled
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
