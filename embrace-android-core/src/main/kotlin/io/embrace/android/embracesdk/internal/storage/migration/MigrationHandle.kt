package io.embrace.android.embracesdk.internal.storage.migration

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The result of a migration job named [name], or [default] if the job fails or never runs.
 */
class MigrationHandle<T>(
    val name: String,
    private val default: T,
    private val errorHandler: ErrorHandler,
) {
    private val latch = CountDownLatch(1)
    private val completed = AtomicBoolean(false)
    private var result: T = default

    /**
     * Blocks until the job completes or [complete] is called, then returns the result. Never throws.
     */
    fun await(): T = errorHandler.guardSdkCode(SdkErrorType.Ignored, default) {
        EmbTrace.trace("migration-await-$name", recordDuration = true) {
            latch.await()
        }
        result
    }

    /**
     * Blocks until the job completes, [complete] is called or [timeoutMs] passes, then returns the result. A timeout is
     * tracked as [errorType] and returns [default]. Never throws.
     */
    fun await(timeoutMs: Long, errorType: SdkErrorType): T = errorHandler.guardSdkCode(SdkErrorType.Ignored, default) {
        val completedInTime = EmbTrace.trace("migration-await-$name", recordDuration = true) {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }
        if (completedInTime) {
            result
        } else {
            errorHandler.trackSdkError(errorType, TimeoutException("migration '$name' not done after $timeoutMs ms"))
            default
        }
    }

    /**
     * True once the job has completed or [complete] has been called.
     */
    val isComplete: Boolean
        get() = latch.count == 0L

    /**
     * Completes with [value] and releases waiters. Only the first call has an effect.
     */
    fun complete(value: T = default) {
        if (completed.compareAndSet(false, true)) {
            result = value
            latch.countDown()
        }
    }

    internal fun run(errorType: SdkErrorType, block: () -> T) {
        complete(
            errorHandler.guardSdkCode(errorType, default) {
                EmbTrace.trace("migration-$name", recordDuration = true, block)
            },
        )
    }
}
