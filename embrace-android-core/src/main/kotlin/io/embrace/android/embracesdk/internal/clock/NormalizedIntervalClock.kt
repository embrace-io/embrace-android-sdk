package io.embrace.android.embracesdk.internal.clock

/**
 * A clock which uses [android.os.SystemClock.elapsedRealtime] that is normalized
 * to the first [System.currentTimeMillis] value.
 *
 * This is useful when it is necessary to perform interval timing but the results must be
 * sent to the API in a way that matches the device time.
 */
internal class NormalizedIntervalClock(systemClock: SystemClock) : Clock {
    private val baseline = systemClock.now() - android.os.SystemClock.elapsedRealtime()

    override fun now(): Long = baseline + android.os.SystemClock.elapsedRealtime()
}
