package io.embrace.android.embracesdk.internal.worker

import java.util.concurrent.Callable
import java.util.concurrent.RunnableFuture

/**
 * An implementation of [RunnableFuture] that also contains priority information on how important
 * the task is.
 */
internal class PriorityRunnableFuture<T> (
    val impl: RunnableFuture<T>,
    val priority: TaskPriority,
    val submitTime: Long
) : RunnableFuture<T> by impl

/**
 * An implementation of [Runnable] that also contains priority information on how important the
 * task is.
 */
internal class PriorityRunnable(
    val priority: TaskPriority,
    impl: Runnable
) : Runnable by impl

/**
 * An implementation of [Callable] that also contains priority information on how important the
 * task is.
 */
internal class PriorityCallable<T>(
    val priority: TaskPriority,
    impl: Callable<T>
) : Callable<T> by impl

/**
 * The priority of a task submitted to the [BackgroundWorker].
 *
 * Tasks with higher priority will be executed first.
 */
internal enum class TaskPriority(

    /**
     * The delay threshold in milliseconds that should be added to the task's submit time
     * when comparing queue priority.
     *
     * The higher the priority, the lower the delay threshold.
     *
     * See [PriorityThreadPoolExecutor] for further detail.
     */
    val delayThresholdMs: Long
) {
    CRITICAL(0),
    HIGH(5_000),
    NORMAL(30_000),
    LOW(60_000)
}
