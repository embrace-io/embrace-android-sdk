package io.embrace.android.embracesdk.internal.capture.metadata.shadows

import android.os.SystemClock
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.time.Clock
import java.time.DateTimeException

/**
 * Robolectric shadow that allows tests to control the values returned by
 * [SystemClock.currentGnssTimeClock] and [SystemClock.currentNetworkTimeClock].
 *
 * Setting [gnssClock] or [networkClock] to `null` causes the corresponding shadowed method to throw
 * [DateTimeException], matching the real Android behaviour when no fix / network time is available.
 */
@Implements(SystemClock::class)
@Suppress("UtilityClassWithPublicConstructor")
class EmbraceShadowSystemClock {
    companion object {
        @JvmStatic
        var gnssClock: Clock? = null

        @JvmStatic
        var networkClock: Clock? = null

        @Implementation
        @JvmStatic
        fun currentGnssTimeClock(): Clock =
            gnssClock ?: throw DateTimeException("GNSS based time is not available")

        @Implementation
        @JvmStatic
        fun currentNetworkTimeClock(): Clock =
            networkClock ?: throw DateTimeException("Network based time is not available")

        fun reset() {
            gnssClock = null
            networkClock = null
        }
    }
}
