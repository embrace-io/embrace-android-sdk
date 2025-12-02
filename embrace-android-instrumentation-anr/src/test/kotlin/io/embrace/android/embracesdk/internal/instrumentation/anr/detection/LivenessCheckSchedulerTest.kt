package io.embrace.android.embracesdk.internal.instrumentation.anr.detection

import android.os.Looper
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeBlockedThreadListener
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.createAnrBehavior
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LivenessCheckSchedulerTest {

    private lateinit var scheduler: LivenessCheckScheduler

    private lateinit var configService: ConfigService
    private lateinit var anrExecutorService: BlockingScheduledExecutorService
    private lateinit var logger: EmbLogger
    private lateinit var looper: Looper
    private lateinit var fakeClock: FakeClock
    private lateinit var state: ThreadMonitoringState
    private lateinit var detector: BlockedThreadDetector
    private lateinit var cfg: AnrRemoteConfig
    private lateinit var anrMonitorThread: AtomicReference<Thread>

    @Before
    fun setUp() {
        anrMonitorThread = AtomicReference(Thread.currentThread())
        cfg = AnrRemoteConfig()
        fakeClock = FakeClock(160982340900)
        configService = FakeConfigService(anrBehavior = createAnrBehavior(remoteCfg = RemoteConfig(anrConfig = cfg)))
        anrExecutorService = BlockingScheduledExecutorService(fakeClock)
        logger = FakeEmbLogger()
        looper = mockk {
            every { thread } returns Thread.currentThread()
        }
        state = ThreadMonitoringState(fakeClock)
        detector = BlockedThreadDetector(
            clock = fakeClock,
            state = state,
            targetThread = Thread.currentThread(),
            blockedDurationThreshold = configService.anrBehavior.getMinDuration(),
            samplingIntervalMs = configService.anrBehavior.getSamplingIntervalMs(),
            listener = FakeBlockedThreadListener(),
        )

        scheduler = LivenessCheckScheduler(
            BackgroundWorker(anrExecutorService),
            fakeClock,
            state,
            looper,
            detector,
            configService.anrBehavior.getSamplingIntervalMs(),
            logger
        )
    }

    @Test
    fun testMonitoringThreadStateWhenDoingStartStopStart() {
        scheduler.startMonitoringThread()
        anrExecutorService.runCurrentlyBlocked()
        assertTrue(state.started.get())

        scheduler.stopMonitoringThread()
        anrExecutorService.runCurrentlyBlocked()
        assertFalse(state.started.get())

        scheduler.startMonitoringThread()
        anrExecutorService.runCurrentlyBlocked()
        assertTrue(state.started.get())
    }

    @Test
    fun testMonitoringThreadStateWhenDoingStartStopStartAndIsDoneReturnsFalse() {
        scheduler.startMonitoringThread()
        anrExecutorService.runCurrentlyBlocked()
        assertTrue(state.started.get())

        scheduler.stopMonitoringThread()
        anrExecutorService.runCurrentlyBlocked()
        assertFalse(state.started.get())

        scheduler.startMonitoringThread()
        anrExecutorService.runCurrentlyBlocked()
        assertTrue(state.started.get())
    }

    @Test
    fun testStartMonitoringThreadDoubleCall() {
        scheduler.startMonitoringThread()
        val lastTimeThreadResponded = fakeClock.now()
        anrExecutorService.runCurrentlyBlocked()
        assertEquals(lastTimeThreadResponded, state.lastMonitorThreadResponseMs)
        fakeClock.tick(10L)
        assertEquals(lastTimeThreadResponded, state.lastMonitorThreadResponseMs)
        // double-start should not schedule anything
        scheduler.startMonitoringThread()
        anrExecutorService.runCurrentlyBlocked()
        assertEquals(lastTimeThreadResponded, state.lastMonitorThreadResponseMs)
    }

    @Test
    fun `starting monitoring thread twice does not result in multiple recurring tasks`() {
        repeat(2) {
            scheduler.startMonitoringThread()
            anrExecutorService.runCurrentlyBlocked()
            assertEquals(1, anrExecutorService.scheduledTasksCount())
        }
    }
}
