package io.embrace.android.embracesdk.worker

import java.util.concurrent.Callable
import java.util.concurrent.RunnableFuture

internal class PriorityRunnableFuture<T> (
    val impl: RunnableFuture<T>,
    val priority: TaskPriority,
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
internal enum class TaskPriority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW;
}
