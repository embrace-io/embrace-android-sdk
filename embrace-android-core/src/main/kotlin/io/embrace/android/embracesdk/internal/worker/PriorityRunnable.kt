package io.embrace.android.embracesdk.internal.worker

/**
 * An implementation of [Runnable] that also contains priority information on how important the
 * task is.
 */
internal class PriorityRunnable(
    val priority: TaskPriority,
    impl: Runnable
) : Runnable by impl
