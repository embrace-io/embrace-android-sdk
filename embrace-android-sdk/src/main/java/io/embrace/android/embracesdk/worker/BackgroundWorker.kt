package io.embrace.android.embracesdk.worker

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * Submits tasks to a background thread pool.
 *
 * This class is necessary because it hides aspects of the ExecutorService API that we don't want
 * to expose as part of the internal API.
 */
internal class BackgroundWorker(
    private val impl: ExecutorService
) {

    /**
     * Submits a task for execution and returns a [Future].
     */
    fun submit(runnable: Runnable): Future<*> = impl.submit(runnable)

    /**
     * Submits a task for execution and returns a [Future].
     */
    fun <T> submit(callable: Callable<T>): Future<T> = impl.submit(callable)
}
