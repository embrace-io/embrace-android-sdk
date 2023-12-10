package io.embrace.android.embracesdk.anr.detection

import android.os.Looper
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

private const val CUSTOM_THREAD_PRIORITY = android.os.Process.THREAD_PRIORITY_BACKGROUND

internal class LivenessCheckSchedulerTest {

    private lateinit var scheduler: LivenessCheckScheduler

    private lateinit var configService: ConfigService
    private lateinit var anrExecutorService: BlockingScheduledExecutorService
    private lateinit var logger: InternalEmbraceLogger
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
        cfg = AnrRemoteConfig(
            monitorThreadPriority = CUSTOM_THREAD_PRIORITY,
        )
        fakeClock = FakeClock(160982340900)
        configService = FakeConfigService(anrBehavior = fakeAnrBehavior { cfg })
        anrExecutorService = BlockingScheduledExecutorService(fakeClock)
        logger = mockk(relaxUnitFun = true)
        looper = mockk {
            every { thread } returns mockk()
        }
        state = ThreadMonitoringState(fakeClock)
        detector = BlockedThreadDetector(
            configService = configService,
            clock = fakeClock,
            state = state,
            targetThread = Thread.currentThread(),
            anrMonitorThread = anrMonitorThread
        )
        fakeTargetThreadHandler = mockk(relaxUnitFun = true) {
            every { action = any() } returns Unit
            every { sendMessage(any()) } returns true
        }
        every { fakeTargetThreadHandler.hasMessages(any()) } returns false

        scheduler = LivenessCheckScheduler(
            configService,
            anrExecutorService,
            fakeClock,
            state,
            fakeTargetThreadHandler,
            detector,
            logger,
            anrMonitorThread = anrMonitorThread
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
        mockkStatic(android.os.Process::class)
        every { fakeTargetThreadHandler.hasMessages(any()) } returns false
        scheduler.checkHeartbeat()

        // verify target thread handler called with scheduling
        verify(exactly = 1) { fakeTargetThreadHandler.sendMessage(any()) }

        // verify heartbeat check
        assertEquals(160982340900, state.lastMonitorThreadResponseMs)
        // verify thread priority property set
        verify(exactly = 1) { android.os.Process.setThreadPriority(CUSTOM_THREAD_PRIORITY) }

        unmockkStatic(android.os.Process::class)
    }

    @Test
    fun testExecuteHealthCheckPendingMessage() {
        mockkStatic(android.os.Process::class)
        every { fakeTargetThreadHandler.hasMessages(any()) } returns true
        scheduler.checkHeartbeat()

        // verify target thread handler called with scheduling
        verify(exactly = 0) { fakeTargetThreadHandler.sendMessage(any()) }

        // verify heartbeat check
        assertEquals(160982340900, state.lastMonitorThreadResponseMs)
        // verify thread priority property set
        verify(exactly = 1) { android.os.Process.setThreadPriority(CUSTOM_THREAD_PRIORITY) }

        unmockkStatic(android.os.Process::class)
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
