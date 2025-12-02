package io.embrace.android.embracesdk.internal.instrumentation.anr.detection

import android.os.Looper
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeThreadBlockageListener
import io.embrace.android.embracesdk.fakes.createAnrBehavior
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.every
import io.mockk.mockk
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
    private lateinit var listener: FakeThreadBlockageListener
    private lateinit var state: ThreadMonitoringState
    private lateinit var anrMonitorThread: AtomicReference<Thread>
    private lateinit var anrExecutorService: BlockingScheduledExecutorService
    private lateinit var logger: EmbLogger
    private lateinit var looper: Looper
    private lateinit var cfg: AnrRemoteConfig

    @Before
    fun setUp() {
        anrMonitorThread = AtomicReference(Thread.currentThread())
        cfg = AnrRemoteConfig()
        clock = FakeClock(BASELINE_MS)
        configService = FakeConfigService(anrBehavior = createAnrBehavior(remoteCfg = RemoteConfig(anrConfig = cfg)))
        anrExecutorService = BlockingScheduledExecutorService(clock)
        logger = FakeEmbLogger()
        looper = mockk {
            every { thread } returns Thread.currentThread()
        }
        state = ThreadMonitoringState(clock)
        listener = FakeThreadBlockageListener()
        detector = BlockedThreadDetector(
            anrMonitorWorker = BackgroundWorker(anrExecutorService),
            clock = clock,
            state = state,
            looper = mockk {
                every { thread } returns Thread.currentThread()
            },
            blockedDurationThreshold = configService.anrBehavior.getMinDuration(),
            intervalMs = configService.anrBehavior.getSamplingIntervalMs(),
            logger = logger,
            listener = listener,
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

    @Test
    fun testStartMonitoringThreadDoubleCall() {
        detector.startMonitoringThread()
        val lastTimeThreadResponded = clock.now()
        anrExecutorService.runCurrentlyBlocked()
        assertEquals(lastTimeThreadResponded, state.lastMonitorThreadResponseMs)
        clock.tick(10L)
        assertEquals(lastTimeThreadResponded, state.lastMonitorThreadResponseMs)
        // double-start should not schedule anything
        detector.startMonitoringThread()
        anrExecutorService.runCurrentlyBlocked()
        assertEquals(lastTimeThreadResponded, state.lastMonitorThreadResponseMs)
    }

    @Test
    fun `starting monitoring thread twice does not result in multiple recurring tasks`() {
        repeat(2) {
            detector.startMonitoringThread()
            anrExecutorService.runCurrentlyBlocked()
            assertEquals(1, anrExecutorService.scheduledTasksCount())
        }
    }
}
