package io.embrace.android.embracesdk.internal.worker

import java.util.concurrent.Callable

/**
 * An implementation of [Callable] that also contains priority information on how important the
 * task is.
 */
internal class PriorityCallable<T>(
    val priority: TaskPriority,
    impl: Callable<T>
) : Callable<T> by impl
