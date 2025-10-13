package io.embrace.android.embracesdk.internal.clock

import android.os.SystemClock

/**
 * A clock which uses [android.os.SystemClock.elapsedRealtime] that is normalized
 * to the first [System.currentTimeMillis] value.
 *
 * This is useful when it is necessary to perform interval timing but the results must be
 * sent to the API in a way that matches the device time.
 */
class NormalizedIntervalClock(timeProvider: () -> Long = System::currentTimeMillis) : Clock {
    private val baseline = timeProvider() - SystemClock.elapsedRealtime()

    override fun now(): Long = baseline + SystemClock.elapsedRealtime()
}
