package io.embrace.android.embracesdk.internal.storage.migration

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Runs migration jobs in submission order on a single daemon thread. The thread is started on first use and exits once
 * it has been idle for [keepAliveMs], so it isn't retained after the migrations finish; a later job starts a new one.
 */
class MigrationWorker(
    private val errorHandler: ErrorHandler,
    threadFactory: ThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, THREAD_NAME).apply { isDaemon = true }
    },
    keepAliveMs: Long = KEEP_ALIVE_MS,
) {
    private val executor = ThreadPoolExecutor(
        1,
        1,
        keepAliveMs,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        threadFactory,
    ).apply { allowCoreThreadTimeOut(true) }

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
        const val KEEP_ALIVE_MS = 1_000L
    }
}
