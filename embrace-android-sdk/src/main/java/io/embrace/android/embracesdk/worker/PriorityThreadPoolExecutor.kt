package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.internal.clock.Clock
import java.util.concurrent.Callable
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.RunnableFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * A [ThreadPoolExecutor] that supports prioritisation of submitted tasks. Prioritisation
 * uses the following logic:
 *
 * 1. Each [TaskPriority] defines a delay threshold in milliseconds.
 * 2. Higher task priorities have smaller delay thresholds.
 * 3. A score is calculated for each task by adding the submit time + delay threshold
 * 4. Tasks are executed in order of lowest score to highest score
 *
 * This helps mitigate against resource starvation scenarios where a large number of high priority
 * tasks prevent lower priority tasks from ever completing execution.
 */
internal class PriorityThreadPoolExecutor(
    private val clock: Clock,
    threadFactory: ThreadFactory,
    handler: RejectedExecutionHandler,
    corePoolSize: Int,
    maximumPoolSize: Int
) : ThreadPoolExecutor(
    corePoolSize,
    maximumPoolSize,
    60L,
    TimeUnit.SECONDS,
    createPriorityQueue(),
    threadFactory,
    handler
) {

    override fun <T : Any?> newTaskFor(callable: Callable<T>): RunnableFuture<T> {
        require(callable is PriorityCallable<T>) {
            "Callable must be PriorityCallable"
        }
        val runnableFuture = super.newTaskFor(callable)
        return PriorityRunnableFuture(runnableFuture, callable.priority, clock.now())
    }

    override fun <T : Any?> newTaskFor(
        runnable: Runnable,
        value: T
    ): RunnableFuture<T> {
        require(runnable is PriorityRunnable) {
            "Runnable must be PriorityCallable"
        }
        val runnableFuture = super.newTaskFor(runnable, value)
        return PriorityRunnableFuture(runnableFuture, runnable.priority, clock.now())
    }

    companion object {
        internal fun createPriorityQueue(): PriorityBlockingQueue<Runnable> =
            PriorityBlockingQueue(
                100,
                compareBy<Runnable> { runnable ->
                    require(runnable is PriorityRunnableFuture<*>) {
                        "Runnable must be PriorityRunnableFuture"
                    }
                    runnable.submitTime + runnable.priority.delayThresholdMs
                }
            )
    }
}
