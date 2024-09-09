package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import java.util.concurrent.atomic.AtomicInteger

internal class LogCounter(
    private val name: String,
    private val getConfigLogLimit: (() -> Int),
    private val logger: EmbLogger
) {
    private val count = AtomicInteger(0)

    fun addIfAllowed(): Boolean {
        if (count.get() < getConfigLogLimit.invoke()) {
            count.incrementAndGet()
        } else {
            logger.logInfo("$name log limit has been reached.")
            return false
        }
        return true
    }

    fun getCount(): Int = count.get()

    fun clear() {
        count.set(0)
    }
}
