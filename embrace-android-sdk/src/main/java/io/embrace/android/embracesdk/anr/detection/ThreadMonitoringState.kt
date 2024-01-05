package io.embrace.android.embracesdk.anr.detection

import io.embrace.android.embracesdk.internal.clock.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class holds state that is used when monitoring a thread. For instance, the last response
 * time of the target/main threads.
 */
internal class ThreadMonitoringState(
    private val clock: Clock
) {

    /**
     * Whether blocked thread detection has already been started or not.
     */
    @JvmField
    val started = AtomicBoolean(false)

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
     * Whether an ANR is already in progress or not.
     */
    @Volatile
    var anrInProgress = false

    /**
     * Resets state properties to the initial values
     */
    fun resetState() {
        anrInProgress = false
        lastTargetThreadResponseMs = clock.now()
        lastMonitorThreadResponseMs = clock.now()
        lastSampleAttemptMs = 0
    }
}
