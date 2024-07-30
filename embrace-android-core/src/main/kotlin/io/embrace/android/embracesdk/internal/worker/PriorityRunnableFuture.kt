package io.embrace.android.embracesdk.internal.worker

import java.util.concurrent.RunnableFuture

/**
 * An implementation of [RunnableFuture] that also contains priority information on how important
 * the task is.
 */
public class PriorityRunnableFuture<T> (
    public val impl: RunnableFuture<T>,
    public val priority: TaskPriority,
    public val submitTime: Long
) : RunnableFuture<T> by impl
