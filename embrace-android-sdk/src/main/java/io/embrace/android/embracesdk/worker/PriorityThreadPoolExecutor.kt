package io.embrace.android.embracesdk.worker

import java.util.concurrent.Callable
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.RunnableFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * A [ThreadPoolExecutor] that supports prioritisation of submitted tasks.
 */
internal class PriorityThreadPoolExecutor(
    threadFactory: ThreadFactory,
    handler: RejectedExecutionHandler
) : ThreadPoolExecutor(
    1,
    1,
    60L,
    TimeUnit.SECONDS,
    createPriorityQueue(),
    threadFactory,
    handler
) {

    override fun <T : Any?> newTaskFor(callable: Callable<T>?): RunnableFuture<T> {
        val runnableFuture = super.newTaskFor(callable)
        val priority = (callable as? PriorityCallable<T>)?.priority
        return PriorityRunnableFuture(runnableFuture, priority ?: TaskPriority.NORMAL)
    }

    override fun <T : Any?> newTaskFor(
        runnable: Runnable?,
        value: T
    ): RunnableFuture<T> {
        val runnableFuture = super.newTaskFor(runnable, value)
        val priority = (runnable as? PriorityRunnable)?.priority
        return PriorityRunnableFuture(runnableFuture, priority ?: TaskPriority.NORMAL)
    }

    companion object {
        private fun createPriorityQueue(): PriorityBlockingQueue<Runnable> =
            PriorityBlockingQueue(
                100,
                compareBy {
                    (it as? PriorityRunnableFuture<*>)?.priority ?: 0
                }
            )
    }
}
