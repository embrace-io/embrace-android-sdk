package io.embrace.android.embracesdk.internal.capture.metadata

import android.app.Application
import android.os.Build.VERSION_CODES.P
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.S
import android.os.Build.VERSION_CODES.TIRAMISU
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DateTimeException

internal class EmbraceMetadataServiceClockDriftTest {

    private val wallTimeMillis = FakeClock.DEFAULT_FAKE_CURRENT_TIME

    private var gnssTimeMillis: Long? = null
    private var networkTimeMillis: Long? = null

    @Test
    fun `getClockDrift returns null below Q regardless of clock availability`() {
        gnssTimeMillis = wallTimeMillis - 50
        networkTimeMillis = wallTimeMillis - 100

        assertNull(computeClockDrift(sdkInt = P))
    }

    @Test
    fun `getClockDrift on Q returns GNSS drift and null network drift`() {
        gnssTimeMillis = wallTimeMillis - 50
        networkTimeMillis = wallTimeMillis - 100

        val drift = computeClockDrift(sdkInt = Q)

        assertEquals(50L, drift?.gnssDriftMillis)
        assertNull(drift?.networkDriftMillis)
    }

    @Test
    fun `getClockDrift on Q returns null when GNSS clock is unavailable`() {
        assertNull(computeClockDrift(sdkInt = Q))
    }

    @Test
    fun `getClockDrift between Q and TIRAMISU does not query network clock`() {
        gnssTimeMillis = wallTimeMillis - 25
        networkTimeMillis = wallTimeMillis - 100

        val drift = computeClockDrift(sdkInt = S)

        assertEquals(25L, drift?.gnssDriftMillis)
        assertNull(drift?.networkDriftMillis)
    }

    @Test
    fun `getClockDrift on TIRAMISU returns both drifts when both clocks are available`() {
        gnssTimeMillis = wallTimeMillis - 50
        networkTimeMillis = wallTimeMillis - 100

        val drift = computeClockDrift(sdkInt = TIRAMISU)

        assertEquals(50L, drift?.gnssDriftMillis)
        assertEquals(100L, drift?.networkDriftMillis)
    }

    @Test
    fun `getClockDrift on TIRAMISU returns only network drift when GNSS is unavailable`() {
        networkTimeMillis = wallTimeMillis - 100

        val drift = computeClockDrift(sdkInt = TIRAMISU)

        assertNull(drift?.gnssDriftMillis)
        assertEquals(100L, drift?.networkDriftMillis)
    }

    @Test
    fun `getClockDrift on TIRAMISU returns only GNSS drift when network is unavailable`() {
        gnssTimeMillis = wallTimeMillis - 50

        val drift = computeClockDrift(sdkInt = TIRAMISU)

        assertEquals(50L, drift?.gnssDriftMillis)
        assertNull(drift?.networkDriftMillis)
    }

    @Test
    fun `getClockDrift on TIRAMISU returns null when both clocks are unavailable`() {
        assertNull(computeClockDrift(sdkInt = TIRAMISU))
    }

    private fun computeClockDrift(sdkInt: Int): ClockDrift? {
        val service = EmbraceMetadataService(
            resourceSource = lazy { FakeEnvelopeResourceSource() },
            context = Application(),
            configService = FakeConfigService(),
            store = FakeKeyValueStore(),
            clock = FakeClock(currentTime = wallTimeMillis),
            metadataBackgroundWorker = fakeBackgroundWorker(),
            versionChecker = VersionChecker { min -> sdkInt >= min },
        )
        return service.computeClockDrift(
            gnssTimeMillis = { gnssTimeMillis ?: throw DateTimeException("GNSS based time is not available") },
            networkTimeMillis = { networkTimeMillis ?: throw DateTimeException("Network based time is not available") },
        )
    }
}
