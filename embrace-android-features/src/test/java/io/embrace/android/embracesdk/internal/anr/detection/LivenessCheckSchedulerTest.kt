package io.embrace.android.embracesdk.internal.anr.detection

import android.os.Looper
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.createAnrBehavior
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

internal class LivenessCheckSchedulerTest {

    private lateinit var scheduler: LivenessCheckScheduler

    private lateinit var configService: ConfigService
    private lateinit var anrExecutorService: BlockingScheduledExecutorService
    private lateinit var logger: EmbLogger
    private lateinit var looper: Looper
    private lateinit var fakeClock: FakeClock
    private lateinit var fakeTargetThreadHandler: TargetThreadHandler
    private lateinit var state: ThreadMonitoringState
    private lateinit var detector: BlockedThreadDetector
    private lateinit var cfg: AnrRemoteConfig
    private lateinit var anrMonitorThread: AtomicReference<Thread>

    @Before
    fun setUp() {
        anrMonitorThread = AtomicReference(Thread.currentThread())
        cfg = AnrRemoteConfig()
        fakeClock = FakeClock(160982340900)
        configService = FakeConfigService(anrBehavior = createAnrBehavior { cfg })
        anrExecutorService = BlockingScheduledExecutorService(fakeClock)
        logger = EmbLoggerImpl()
        looper = mockk {
            every { thread } returns Thread.currentThread()
        }
        state = ThreadMonitoringState(fakeClock)
        detector = BlockedThreadDetector(
            configService = configService,
            clock = fakeClock,
            state = state,
            targetThread = Thread.currentThread()
        )
        fakeTargetThreadHandler = mockk(relaxUnitFun = true) {
            every { action = any() } returns Unit
            every { sendMessage(any()) } returns true
        }
        every { fakeTargetThreadHandler.hasMessages(any()) } returns false

        scheduler = LivenessCheckScheduler(
            configService,
            BackgroundWorker(anrExecutorService),
            fakeClock,
            state,
            fakeTargetThreadHandler,
            detector,
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
    fun testGetConfigService() {
        assertEquals(configService, scheduler.configService)
        val obj = mockk<ConfigService>()
        scheduler.configService = obj
        assertEquals(obj, scheduler.configService)
    }

    @Test
    fun testExecuteHealthCheckSameInterval() {
        every { fakeTargetThreadHandler.hasMessages(any()) } returns false
        scheduler.checkHeartbeat()

        // verify target thread handler called with scheduling
        verify(exactly = 1) { fakeTargetThreadHandler.sendMessage(any()) }

        // verify heartbeat check
        assertEquals(160982340900, state.lastMonitorThreadResponseMs)
    }

    @Test
    fun testExecuteHealthCheckPendingMessage() {
        every { fakeTargetThreadHandler.hasMessages(any()) } returns true
        scheduler.checkHeartbeat()

        // verify target thread handler called with scheduling
        verify(exactly = 0) { fakeTargetThreadHandler.sendMessage(any()) }

        // verify heartbeat check
        assertEquals(160982340900, state.lastMonitorThreadResponseMs)
    }

    @Test
    fun testExecuteHealthCheckDifferentIntervalMs() {
        // alter the intervalMs to trigger rescheduling
        scheduler.startMonitoringThread()
        anrExecutorService.runCurrentlyBlocked()
        assertEquals(fakeClock.now(), state.lastMonitorThreadResponseMs)
        cfg = cfg.copy(sampleIntervalMs = 10)
        assertEquals(1, anrExecutorService.scheduledTasksCount())
        anrExecutorService.moveForwardAndRunBlocked(100)
        anrExecutorService.runCurrentlyBlocked()
        anrExecutorService.moveForwardAndRunBlocked(10)
        assertEquals(fakeClock.now(), state.lastMonitorThreadResponseMs)
        assertEquals(1, anrExecutorService.scheduledTasksCount())
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
