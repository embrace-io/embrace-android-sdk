package io.embrace.android.embracesdk.utils

import io.embrace.android.embracesdk.annotation.InternalApi
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException

/**
 * Submit a [Callable] such that if the submit fails due to a [RejectedExecutionException], it simply does nothing
 */
@InternalApi
internal fun <T> ExecutorService.submitSafe(task: Callable<T>): Future<T>? {
    return try {
        submit(task)
    } catch (e: RejectedExecutionException) {
        null
    }
}

/**
 * Eagerly loads the value returned by a Lazy property on a background thread.
 *
 * When the Lazy property is accessed for the first time, the value is returned if it is already loaded, or the
 * main thread is blocked until it finishes loading.
 */
@InternalApi
internal fun <T> ExecutorService.eagerLazyLoad(task: Callable<T>): Lazy<T> {
    val future = submitSafe(task)
    return lazy {
        if (future != null) {
            try {
                return@lazy future.get()
            } catch (e: Exception) {
                return@lazy getCallableValue<T>(task)
            }
        }
        getCallableValue(task)
    }
}

private fun <T> getCallableValue(task: Callable<T>): T {
    return try {
        task.call()
    } catch (e: Exception) {
        throw IllegalStateException("Failed to load property.", e)
    }
}
