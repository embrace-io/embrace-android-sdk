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
     * Returns true if [snapshot] should be recorded, which uses one from the limit.
     */
    fun tryAcquire(snapshot: SdkErrorSnapshot): Boolean {
        val key = SdkErrorKey(snapshot.type, snapshot.top.className, snapshot.top.frames.firstOrNull())
        if (recorded.containsKey(key) || !reserve()) {
            return false
        }
        if (recorded.putIfAbsent(key, Unit) != null) {
            remaining.incrementAndGet()
            return false
        }
        return true
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
