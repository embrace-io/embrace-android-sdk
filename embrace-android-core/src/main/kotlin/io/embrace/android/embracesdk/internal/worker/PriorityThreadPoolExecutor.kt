package io.embrace.android.embracesdk.internal.worker

import java.util.concurrent.Callable
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.RunnableFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * A [ThreadPoolExecutor] that supports prioritisation of submitted tasks. Each task
 * must have a priorityInfo object that is associated with the task. Prioritisation is then
 * delegated to the supplied [Comparator].
 *
 * This executor assumes that all tasks submitted are operations that have some sort of logical
 * grouping that can be prioritised. Submitting all HTTP requests to the same executor will result
 * in good prioritisation. Submitting a bunch of miscellaneous tasks that have no relation to each
 * other will probably not.
 */
internal class PriorityThreadPoolExecutor(
    threadFactory: ThreadFactory,
    handler: RejectedExecutionHandler,
    corePoolSize: Int,
    maximumPoolSize: Int,
    comparator: Comparator<Runnable>
) : ThreadPoolExecutor(
    corePoolSize,
    maximumPoolSize,
    60L,
    TimeUnit.SECONDS,
    PriorityBlockingQueue(100, comparator),
    threadFactory,
    handler
) {

    override fun <T : Any?> newTaskFor(callable: Callable<T>): RunnableFuture<T> {
        require(callable is PriorityCallable<T>) {
            "Callable must be PriorityCallable"
        }
        val runnableFuture = super.newTaskFor(callable)
        return PriorityRunnableFuture<T>(runnableFuture, callable.priorityInfo)
    }

    override fun <T : Any?> newTaskFor(
        runnable: Runnable,
        value: T
    ): RunnableFuture<T> {
        require(runnable is PriorityRunnable) {
            "Runnable must be PriorityCallable"
        }
        val runnableFuture = super.newTaskFor(runnable, value)
        return PriorityRunnableFuture<T>(runnableFuture, runnable.priorityInfo)
    }
}
