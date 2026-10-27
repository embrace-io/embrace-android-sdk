package io.embrace.android.embracesdk.internal.logging

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Limits SDK errors to at most [limit] unique errors per session part. Repeats are distinguished by [SdkErrorKey] and do not
 * count to the limit.
 */
internal class SdkErrorLimiter(limit: Int) {

    private val remaining = AtomicInteger(limit)
    private val recorded = ConcurrentHashMap<SdkErrorKey, Unit>()

    /**
     * Returns true if [throwable] should be recorded as an error of [type], which uses one from the limit.
     */
    fun tryAcquire(type: SdkErrorType, throwable: Throwable): Boolean {
        if (remaining.get() <= 0) {
            return false
        }
        val key = SdkErrorKey(type, throwable.javaClass, throwable.stackTrace.firstOrNull())
        if (recorded.putIfAbsent(key, Unit) != null) {
            return false
        }
        while (true) {
            val current = remaining.get()
            if (current <= 0) {
                recorded.remove(key)
                return false
            }
            if (remaining.compareAndSet(current, current - 1)) {
                return true
            }
        }
    }
}
