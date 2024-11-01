package io.embrace.android.embracesdk.internal.anr.detection

import io.embrace.android.embracesdk.fakes.FakeBlockedThreadListener
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.internal.config.ConfigService
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
    private lateinit var listener: FakeBlockedThreadListener
    private lateinit var state: ThreadMonitoringState
    private lateinit var anrMonitorThread: AtomicReference<Thread>

    @Before
    fun setUp() {
        configService = FakeConfigService()
        clock = FakeClock(BASELINE_MS)
        listener = FakeBlockedThreadListener()
        state = ThreadMonitoringState(clock)
        anrMonitorThread = AtomicReference(Thread.currentThread())
        detector = BlockedThreadDetector(
            configService,
            clock,
            listener,
            state,
            Thread.currentThread()
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
        assertEquals(1, listener.intervalCount)
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
        assertEquals(0, listener.intervalCount)
        assertEquals(now, state.lastMonitorThreadResponseMs)
        assertEquals(now - 10, state.lastSampleAttemptMs)
    }
}
