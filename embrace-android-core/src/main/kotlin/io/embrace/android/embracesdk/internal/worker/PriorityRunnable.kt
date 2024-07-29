package io.embrace.android.embracesdk.internal.worker

/**
 * An implementation of [Runnable] that also contains priority information on how important the
 * task is.
 */
public class PriorityRunnable(
    public val priority: TaskPriority,
    impl: Runnable
) : Runnable by impl
