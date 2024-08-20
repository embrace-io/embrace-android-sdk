package io.embrace.android.embracesdk.internal.worker

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
