package io.embrace.android.embracesdk.capture.memory

import io.embrace.android.embracesdk.clock.Clock
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.MemoryWarning
import java.util.NavigableMap
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Polls for the device's available and used memory.
 *
 * Stores memory warnings when the [ActivityService] detects a memory trim event.
 */
internal class EmbraceMemoryService(
    private val clock: Clock
) : MemoryService {

    private val memoryTimestamps = LongArray(MAX_CAPTURED_MEMORY_WARNINGS)
    private var offset = 0

    override fun onMemoryWarning() {
        InternalStaticEmbraceLogger.logDeveloper(
            "EmbraceMemoryService",
            "Memory warning number: $offset"
        )
        if (offset < MAX_CAPTURED_MEMORY_WARNINGS) {
            memoryTimestamps[offset] = clock.now()
            offset++
        }
    }

    override fun getCapturedData(): List<MemoryWarning> {
        val memoryWarnings: NavigableMap<Long, MemoryWarning> = ConcurrentSkipListMap()
        for (i in 0 until offset) {
            memoryWarnings[memoryTimestamps[i]] = MemoryWarning(memoryTimestamps[i])
        }
        return ArrayList(memoryWarnings.subMap(0, Long.MAX_VALUE).values)
    }

    override fun cleanCollections() {
        offset = 0
    }

    companion object {
        private const val MAX_CAPTURED_MEMORY_WARNINGS = 100
    }
}
