package io.embrace.android.embracesdk.internal.storage.migration

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Bounds the total time spent waiting on migration jobs to [budgetMs], however many waits there are.
 */
class MigrationBudget(
    private val worker: MigrationWorker,
    private val errorHandler: ErrorHandler,
    budgetMs: Long = BUDGET_MS,
) {
    private val lock = Any()

    private var remainingMs: Long = budgetMs

    private var timedOut: Boolean = false

    /**
     * Blocks until [handle] completes or the budget runs out, then returns its result, or its default after a timeout.
     * The first timeout is tracked as the error type of the job stalling the worker, or [errorType] if none is running.
     */
    fun <T> await(handle: MigrationHandle<T>, errorType: SdkErrorType): T = synchronized(lock) {
        val start = System.nanoTime()
        try {
            handle.await(remainingMs.coerceAtLeast(0)) {
                if (!timedOut) {
                    timedOut = true
                    errorHandler.trackSdkError(
                        worker.runningErrorType ?: errorType,
                        TimeoutException("migration '${handle.name}' not done within the budget"),
                    )
                }
            }
        } finally {
            remainingMs -= TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        }
    }

    companion object {
        const val BUDGET_MS: Long = 3_000L
    }
}
