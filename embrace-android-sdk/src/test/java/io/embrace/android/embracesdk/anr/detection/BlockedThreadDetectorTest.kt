package io.embrace.android.embracesdk.anr.detection

import io.embrace.android.embracesdk.anr.BlockedThreadListener
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

private const val BASELINE_MS = 1500000000L

internal class BlockedThreadDetectorTest {

    private lateinit var detector: BlockedThreadDetector
    private lateinit var configService: ConfigService
    private lateinit var clock: FakeClock
    private lateinit var listener: BlockedThreadListener
    private lateinit var state: ThreadMonitoringState
    private lateinit var anrMonitorThread: AtomicReference<Thread>

    @Before
    fun setUp() {
        configService = FakeConfigService()
        clock = FakeClock(BASELINE_MS)
        listener = mockk(relaxUnitFun = true)
        state = ThreadMonitoringState(clock)
        anrMonitorThread = AtomicReference(Thread.currentThread())
        detector = BlockedThreadDetector(
            configService,
            clock,
            listener,
            state,
            Thread.currentThread(),
            anrMonitorThread = anrMonitorThread
        )
    }

    @Test
    fun testShouldAttemptAnrSample() {
        assertFalse(detector.shouldAttemptAnrSample(BASELINE_MS))
        assertFalse(detector.shouldAttemptAnrSample(-23409))
        assertFalse(detector.shouldAttemptAnrSample(0))
        assertFalse(detector.shouldAttemptAnrSample(BASELINE_MS - 23409))
        assertFalse(detector.shouldAttemptAnrSample(BASELINE_MS + 50))
        assertTrue(detector.shouldAttemptAnrSample(BASELINE_MS + 51))
        assertTrue(detector.shouldAttemptAnrSample(BASELINE_MS + 100))
        assertTrue(detector.shouldAttemptAnrSample(BASELINE_MS + 30000))
    }

    @Test
    fun testListenerFired() {
        val now = BASELINE_MS + 3000
        clock.setCurrentTime(now)
        detector.updateAnrTracking(BASELINE_MS + 2000)
        verify(exactly = 1) { listener.onThreadBlockedInterval(any(), any()) }
        assertEquals(now, state.lastMonitorThreadResponseMs)
        assertEquals(now, state.lastSampleAttemptMs)
    }

    @Test
    fun testSampleBackoff() {
        val now = BASELINE_MS + 2000
        clock.setCurrentTime(now)
        state.lastMonitorThreadResponseMs = now - 10
        state.lastSampleAttemptMs = now - 10

        detector.updateAnrTracking(now)
        verify(exactly = 0) { listener.onThreadBlockedInterval(any(), any()) }
        assertEquals(now, state.lastMonitorThreadResponseMs)
        assertEquals(now - 10, state.lastSampleAttemptMs)
    }
}
