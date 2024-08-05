package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.CacheableValue
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import java.util.NavigableMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger

public class LogCounter(
    private val name: String,
    private val clock: Clock,
    private val getConfigLogLimit: (() -> Int),
    private val logger: EmbLogger
) {
    private val count = AtomicInteger(0)
    private val logIds: NavigableMap<Long, String> = ConcurrentSkipListMap()
    private val cache = CacheableValue<List<String>> { logIds.size }

    public fun addIfAllowed(logId: String): Boolean {
        val timestamp = clock.now()
        count.incrementAndGet()

        if (logIds.size < getConfigLogLimit.invoke()) {
            logIds[timestamp] = logId
        } else {
            logger.logInfo("$name log limit has been reached.")
            return false
        }
        return true
    }

    public fun findLogIds(): List<String> {
        return cache.value { ArrayList(logIds.values) }
    }

    public fun getCount(): Int = count.get()

    public fun clear() {
        count.set(0)
        logIds.clear()
    }
}
