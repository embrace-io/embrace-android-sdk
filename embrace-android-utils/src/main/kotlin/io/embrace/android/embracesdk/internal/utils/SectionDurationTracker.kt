package io.embrace.android.embracesdk.internal.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * Records elapsed times in milliseconds keyed by section name until [flush] is called.
 * The first recording for a given section is retained, while subsequent ones are discarded.
 */
class SectionDurationTracker {

    @Volatile
    private var enabled: Boolean = true

    private val durationsMs = ConcurrentHashMap<String, Long>()

    /**
     * Record [durationMs] against [sectionName], unless a duration was already recorded for that
     * name or recording has been disabled by [flush].
     */
    fun record(sectionName: String, durationMs: Long) {
        if (enabled) {
            durationsMs.putIfAbsent(sectionName, durationMs)
        }
    }

    /**
     * Disable recording, snapshot the already-recorded section durations, clear the stored recordings,
     * and return the snapshot.
     */
    fun flush(): Map<String, Long> {
        enabled = false
        val snapshot = durationsMs.toMap()
        durationsMs.clear()
        return snapshot
    }

    /**
     * Re-enable recording with cleared state.
     */
    fun reset() {
        durationsMs.clear()
        enabled = true
    }
}
