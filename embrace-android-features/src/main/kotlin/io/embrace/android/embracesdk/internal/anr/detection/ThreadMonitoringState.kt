package io.embrace.android.embracesdk.internal.anr.detection

import io.embrace.android.embracesdk.internal.clock.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class holds state that is used when monitoring a thread. For instance, the last response
 * time of the target/main threads.
 */
public class ThreadMonitoringState(
    private val clock: Clock
) {

    /**
     * Whether blocked thread detection has already been started or not.
     */
    @JvmField
    public val started: AtomicBoolean = AtomicBoolean(false)

    /**
     * The last response time of the target thread in ms.
     */
    @Volatile
    public var lastTargetThreadResponseMs: Long = clock.now()

    /**
     * The last response time of the monitoring thread in ms.
     */
    @Volatile
    public var lastMonitorThreadResponseMs: Long = clock.now()

    /**
     * The last sample attempt in ms.
     */
    @Volatile
    public var lastSampleAttemptMs: Long = 0

    /**
     * Whether an ANR is already in progress or not.
     */
    @Volatile
    public var anrInProgress: Boolean = false

    /**
     * Resets state properties to the initial values
     */
    public fun resetState() {
        anrInProgress = false
        lastTargetThreadResponseMs = clock.now()
        lastMonitorThreadResponseMs = clock.now()
        lastSampleAttemptMs = 0
    }
}
