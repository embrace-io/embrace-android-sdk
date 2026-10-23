package io.embrace.android.embracesdk.internal.logs

import java.util.concurrent.atomic.AtomicInteger

internal class LogCounter(
    private val getConfigLogLimit: () -> Int,
) {
    private val count = AtomicInteger(0)

    fun addIfAllowed(): Boolean {
        val limit = getConfigLogLimit()
        while (true) {
            val current = count.get()
            if (current >= limit) {
                return false
            }
            if (count.compareAndSet(current, current + 1)) {
                return true
            }
        }
    }

    fun getCount(): Int = count.get()

    fun clear() {
        count.set(0)
    }
}
