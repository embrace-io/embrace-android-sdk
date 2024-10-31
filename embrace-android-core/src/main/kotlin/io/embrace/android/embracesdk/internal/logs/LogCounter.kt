package io.embrace.android.embracesdk.internal.logs

import java.util.concurrent.atomic.AtomicInteger

internal class LogCounter(
    private val getConfigLogLimit: () -> Int,
) {
    private val count = AtomicInteger(0)

    fun addIfAllowed(): Boolean {
        if (count.get() < getConfigLogLimit.invoke()) {
            count.incrementAndGet()
        } else {
            return false
        }
        return true
    }

    fun getCount(): Int = count.get()

    fun clear() {
        count.set(0)
    }
}
