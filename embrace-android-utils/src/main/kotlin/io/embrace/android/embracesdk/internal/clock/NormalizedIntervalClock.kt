package io.embrace.android.embracesdk.internal.clock

import android.os.SystemClock
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
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
    private val lastTime = AtomicLong(0L)

    override fun now(): Long {
        val newTime = baseline + monotonicClock()
        val prev = lastTime.getAndSet(newTime)
        if (prev > 0L && newTime < prev - driftThresholdMs) {
            logger?.trackInternalError(
                InternalErrorType.INTERNAL_INTERFACE_FAIL,
                IllegalStateException("NormalizedIntervalClock drifted back in time by more than threshold. Delivery is likely out-of-order.")
            )
        }
        return newTime
    }

    companion object {
        const val DEFAULT_DRIFT_THRESHOLD_MS: Long = 60_000L
    }
}
