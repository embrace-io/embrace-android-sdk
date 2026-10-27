package io.embrace.android.embracesdk.internal.capture.metadata

import android.app.Application
import android.os.Build.VERSION_CODES.P
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.S
import android.os.Build.VERSION_CODES.TIRAMISU
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
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

    @Test
    fun `clock drift is computed when capturing other metadata fails`() {
        gnssTimeMillis = wallTimeMillis - 50
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val service = createService(
            sdkInt = TIRAMISU,
            resourceSource = lazy { error("resource failure") },
            errorHandler = errorHandler,
        )

        service.precomputeValues()

        assertEquals(50L, service.getClockDrift()?.gnssDriftMillis)
        // the disk usage can't be captured on the JVM either, so it may be reported too
        assertEquals(setOf(SdkErrorType.SafeDataCaptureFail.toString()), errorHandler.sdkErrorMessages.map { it.msg }.toSet())
    }

    private fun computeClockDrift(sdkInt: Int): ClockDrift? =
        createService(sdkInt = sdkInt).computeClockDrift(gnssTimeMillis = ::gnssTime, networkTimeMillis = ::networkTime)

    private fun createService(
        sdkInt: Int,
        resourceSource: Lazy<EnvelopeResourceSource> = lazy { FakeEnvelopeResourceSource() },
        errorHandler: FakeErrorHandler = FakeErrorHandler(),
    ) = EmbraceMetadataService(
        resourceSource = resourceSource,
        context = Application(),
        configService = FakeConfigService(),
        store = FakeKeyValueStore(),
        clock = FakeClock(currentTime = wallTimeMillis),
        metadataBackgroundWorker = fakeBackgroundWorker(),
        errorHandler = errorHandler,
        versionChecker = VersionChecker { min -> sdkInt >= min },
        gnssTimeMillis = ::gnssTime,
        networkTimeMillis = ::networkTime,
    )

    private fun gnssTime(): Long = gnssTimeMillis ?: throw DateTimeException("GNSS based time is not available")

    private fun networkTime(): Long = networkTimeMillis ?: throw DateTimeException("Network based time is not available")
}
