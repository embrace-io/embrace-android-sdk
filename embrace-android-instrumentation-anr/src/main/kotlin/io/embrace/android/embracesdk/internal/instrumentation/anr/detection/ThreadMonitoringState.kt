package io.embrace.android.embracesdk.internal.instrumentation.anr.detection

import io.embrace.android.embracesdk.internal.clock.Clock

/**
 * This class holds state that is used when monitoring a thread. For instance, the last response
 * time of the target/main threads.
 */
internal class ThreadMonitoringState(
    private val clock: Clock,
) {

    /**
     * The last response time of the target thread in ms.
     */
    @Volatile
    var lastTargetThreadResponseMs: Long = clock.now()

    /**
     * The last response time of the monitoring thread in ms.
     */
    @Volatile
    var lastMonitorThreadResponseMs: Long = clock.now()

    /**
     * The last sample attempt in ms.
     */
    @Volatile
    var lastSampleAttemptMs: Long = 0

    /**
     * Whether the thread blockage is in progress or not.
     */
    @Volatile
    var threadBlockageInProgress: Boolean = false

    /**
     * Resets state properties to the initial values
     */
    fun resetState() {
        threadBlockageInProgress = false
        lastTargetThreadResponseMs = clock.now()
        lastMonitorThreadResponseMs = clock.now()
        lastSampleAttemptMs = 0
    }
}
