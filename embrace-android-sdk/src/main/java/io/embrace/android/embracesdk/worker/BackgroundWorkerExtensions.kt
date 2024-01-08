package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.annotation.InternalApi
import java.util.concurrent.Callable

/**
 * Eagerly loads the value returned by a Lazy property on a background thread.
 *
 * When the Lazy property is accessed for the first time, the value is returned if it is
 * already loaded, or the main thread is blocked until it finishes loading.
 */
@InternalApi
internal fun <T> BackgroundWorker.eagerLazyLoad(task: Callable<T>): Lazy<T> {
    val future = submit(task)
    return lazy {
        try {
            return@lazy future.get()
        } catch (e: Exception) {
            return@lazy getCallableValue<T>(task)
        }
    }
}

private fun <T> getCallableValue(task: Callable<T>): T {
    return try {
        task.call()
    } catch (e: Exception) {
        throw IllegalStateException("Failed to load property.", e)
    }
}
