package io.embrace.android.embracesdk.internal.worker

/**
 * An implementation of [Runnable] that also contains priority information on how important the
 * task is.
 */
class PriorityRunnable(
    val priorityInfo: Any,
    impl: Runnable
) : Runnable by impl
