package io.embrace.android.embracesdk.internal.storage.migration

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Runs migration jobs in submission order on a single daemon thread, started on first use.
 */
internal class MigrationWorker(
    private val errorHandler: ErrorHandler,
    threadFactory: ThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, THREAD_NAME).apply { isDaemon = true }
    },
) {
    private val executor: ExecutorService by lazy { Executors.newSingleThreadExecutor(threadFactory) }

    /**
     * Submits [block] as the job named [name]. A failure is tracked as [errorType] and completes the handle with [default].
     */
    fun <T> submit(name: String, default: T, errorType: SdkErrorType, block: () -> T): MigrationHandle<T> =
        MigrationHandle(name, default, errorHandler).also { submit(it, errorType, block) }

    /**
     * Submits [block] to complete [handle]. If it can't be queued, [handle] completes with its default.
     */
    fun <T> submit(handle: MigrationHandle<T>, errorType: SdkErrorType, block: () -> T) {
        val queued = errorHandler.guardSdkCode(errorType, false) {
            executor.execute { handle.run(errorType, block) }
            true
        }
        if (!queued) {
            handle.complete()
        }
    }

    private companion object {
        const val THREAD_NAME = "emb-migration"
    }
}
