package io.embrace.android.embracesdk.internal.worker

import java.util.concurrent.Callable

/**
 * An implementation of [Callable] that also contains priority information on how important the
 * task is.
 */
class PriorityCallable<T>(
    val priorityInfo: Any,
    impl: Callable<T>
) : Callable<T> by impl
