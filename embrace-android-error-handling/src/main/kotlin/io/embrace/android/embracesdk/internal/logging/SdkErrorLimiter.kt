package io.embrace.android.embracesdk.internal.logging

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Limits SDK errors to at most [limit] unique errors per session part. Repeats are distinguished by [SdkErrorKey] and do not
 * count to the limit. At most [limit] keys are ever held.
 */
internal class SdkErrorLimiter(limit: Int) {

    private val remaining = AtomicInteger(limit)
    private val recorded = ConcurrentHashMap<SdkErrorKey, Unit>()

    /**
     * True once the limit is used up.
     */
    val exhausted: Boolean
        get() = remaining.get() <= 0

    /**
     * Returns true if an error with [key] should be recorded, which uses one from the limit.
     */
    fun tryAcquire(key: SdkErrorKey): Boolean {
        if (recorded.containsKey(key) || !reserve()) {
            return false
        }
        if (recorded.putIfAbsent(key, Unit) != null) {
            remaining.incrementAndGet()
            return false
        }
        return true
    }

    /**
     * Gives back the slot taken by [tryAcquire] for an error that wasn't recorded.
     */
    fun release(key: SdkErrorKey) {
        if (recorded.remove(key) != null) {
            remaining.incrementAndGet()
        }
    }

    private fun reserve(): Boolean {
        while (true) {
            val current = remaining.get()
            if (current <= 0) {
                return false
            }
            if (remaining.compareAndSet(current, current - 1)) {
                return true
            }
        }
    }
}
