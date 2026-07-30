package io.embrace.android.embracesdk.internal.clock

import android.os.SystemClock
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * A clock which uses [android.os.SystemClock.elapsedRealtime] that is normalized
 * to the first [System.currentTimeMillis] value.
 *
 * This is useful when it is necessary to perform interval timing but the results must be
 * sent to the API in a way that matches the device time.
 *
 * If [logger] is provided, an error is logged whenever successive [now] calls detect that time
 * drifted backwards by more than [driftThresholdMs] milliseconds.
 */
class NormalizedIntervalClock(
    private val logger: InternalLogger? = null,
    private val driftThresholdMs: Long = DEFAULT_DRIFT_THRESHOLD_MS,
    wallClock: () -> Long = System::currentTimeMillis,
    private val monotonicClock: () -> Long = SystemClock::elapsedRealtime,
) : Clock {

    private val baseline = wallClock() - monotonicClock()

    /**
     * The lastTime that `now` was called - `Long.MIN_VALUE` as a "not yet tracked" sentinel.
     */
    private val lastTime = AtomicLong(Long.MIN_VALUE)
    private val hasLoggedDrift = AtomicBoolean(false)

    override fun now(): Long {
        while (true) {
            val prev = lastTime.get()
            val newTime = baseline + monotonicClock()

            if (prev != Long.MIN_VALUE && newTime < prev - driftThresholdMs) {
                if (hasLoggedDrift.compareAndSet(false, true)) {
                    logger?.trackInternalError(
                        InternalErrorType.InternalInterfaceFail,
                        IllegalStateException(
                            "NormalizedIntervalClock drifted back in time by more than threshold. Delivery is likely out-of-order.",
                        ),
                    )
                }
            }

            // never lower the high-water mark: a backwards reading is returned but not stored,
            // otherwise repeated sub-threshold steps would walk the comparison baseline down.
            if (newTime <= prev || lastTime.compareAndSet(prev, newTime)) {
                return newTime
            }
        }
    }

    companion object {
        const val DEFAULT_DRIFT_THRESHOLD_MS: Long = 60_000L
    }
}
