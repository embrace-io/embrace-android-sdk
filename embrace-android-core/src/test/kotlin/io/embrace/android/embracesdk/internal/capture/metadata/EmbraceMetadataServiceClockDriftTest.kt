package io.embrace.android.embracesdk.internal.capture.metadata

import android.app.Application
import android.app.usage.StorageStatsManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.capture.metadata.shadows.EmbraceShadowSystemClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(shadows = [EmbraceShadowSystemClock::class])
internal class EmbraceMetadataServiceClockDriftTest {

    private val wallTimeMillis = FakeClock.DEFAULT_FAKE_CURRENT_TIME
    private lateinit var service: EmbraceMetadataService

    @Before
    fun setUp() {
        EmbraceShadowSystemClock.reset()
        service = EmbraceMetadataService(
            resourceSource = lazy { FakeEnvelopeResourceSource() },
            context = ApplicationProvider.getApplicationContext<Application>(),
            storageStatsManager = lazy<StorageStatsManager?> { null },
            configService = FakeConfigService(),
            store = FakeKeyValueStore(),
            clock = FakeClock(currentTime = wallTimeMillis),
            metadataBackgroundWorker = fakeBackgroundWorker(),
        )
    }

    @After
    fun tearDown() {
        EmbraceShadowSystemClock.reset()
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `getClockDrift returns null below Q regardless of clock availability`() {
        EmbraceShadowSystemClock.gnssClock = fixedClock(wallTimeMillis - 50)
        EmbraceShadowSystemClock.networkClock = fixedClock(wallTimeMillis - 100)

        service.precomputeValues()
        assertNull(service.getClockDrift())
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `getClockDrift on Q returns GNSS drift and null network drift`() {
        EmbraceShadowSystemClock.gnssClock = fixedClock(wallTimeMillis - 50)
        EmbraceShadowSystemClock.networkClock = fixedClock(wallTimeMillis - 100)

        service.precomputeValues()
        val drift = service.getClockDrift()

        assertEquals(50L, drift?.gnssDriftMillis)
        assertNull(drift?.networkDriftMillis)
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `getClockDrift on Q returns null when GNSS clock is unavailable`() {
        service.precomputeValues()
        assertNull(service.getClockDrift())
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `getClockDrift between Q and TIRAMISU does not query network clock`() {
        EmbraceShadowSystemClock.gnssClock = fixedClock(wallTimeMillis - 25)
        EmbraceShadowSystemClock.networkClock = fixedClock(wallTimeMillis - 100)

        service.precomputeValues()
        val drift = service.getClockDrift()

        assertEquals(25L, drift?.gnssDriftMillis)
        assertNull(drift?.networkDriftMillis)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `getClockDrift on TIRAMISU returns both drifts when both clocks are available`() {
        EmbraceShadowSystemClock.gnssClock = fixedClock(wallTimeMillis - 50)
        EmbraceShadowSystemClock.networkClock = fixedClock(wallTimeMillis - 100)

        service.precomputeValues()
        val drift = service.getClockDrift()

        assertEquals(50L, drift?.gnssDriftMillis)
        assertEquals(100L, drift?.networkDriftMillis)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `getClockDrift on TIRAMISU returns only network drift when GNSS is unavailable`() {
        EmbraceShadowSystemClock.networkClock = fixedClock(wallTimeMillis - 100)

        service.precomputeValues()
        val drift = service.getClockDrift()

        assertNull(drift?.gnssDriftMillis)
        assertEquals(100L, drift?.networkDriftMillis)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `getClockDrift on TIRAMISU returns only GNSS drift when network is unavailable`() {
        EmbraceShadowSystemClock.gnssClock = fixedClock(wallTimeMillis - 50)

        service.precomputeValues()
        val drift = service.getClockDrift()

        assertEquals(50L, drift?.gnssDriftMillis)
        assertNull(drift?.networkDriftMillis)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `getClockDrift on TIRAMISU returns null when both clocks are unavailable`() {
        service.precomputeValues()
        assertNull(service.getClockDrift())
    }

    private fun fixedClock(epochMillis: Long): Clock =
        Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
}
