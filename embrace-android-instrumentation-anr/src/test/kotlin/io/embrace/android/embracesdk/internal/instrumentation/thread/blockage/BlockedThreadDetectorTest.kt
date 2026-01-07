package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import android.os.Looper
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeThreadBlockageListener
import io.embrace.android.embracesdk.fakes.createThreadBlockageBehavior
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.ThreadBlockageRemoteConfig
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

private const val BASELINE_MS = 1500000000L

internal class BlockedThreadDetectorTest {

    private lateinit var detector: BlockedThreadDetector
    private lateinit var configService: ConfigService
    private lateinit var clock: FakeClock
    private lateinit var listener: FakeThreadBlockageListener
    private lateinit var watchdogThread: AtomicReference<Thread>
    private lateinit var watchdogExecutorService: BlockingScheduledExecutorService
    private lateinit var logger: InternalLogger
    private lateinit var looper: Looper
    private lateinit var cfg: ThreadBlockageRemoteConfig

    @Before
    fun setUp() {
        watchdogThread = AtomicReference(Thread.currentThread())
        cfg = ThreadBlockageRemoteConfig()
        clock = FakeClock(BASELINE_MS)
        configService = FakeConfigService(
            threadBlockageBehavior = createThreadBlockageBehavior(
                remoteCfg = RemoteConfig(
                    threadBlockageRemoteConfig = cfg
                )
            )
        )
        watchdogExecutorService = BlockingScheduledExecutorService(clock)
        logger = FakeInternalLogger()
        looper = mockk {
            every { thread } returns Thread.currentThread()
        }
        listener = FakeThreadBlockageListener()
        detector = BlockedThreadDetector(
            watchdogWorker = BackgroundWorker(watchdogExecutorService),
            clock = clock,
            looper = mockk {
                every { thread } returns Thread.currentThread()
            },
            blockedDurationThreshold = configService.threadBlockageBehavior.getMinDuration(),
            intervalMs = configService.threadBlockageBehavior.getSamplingIntervalMs(),
            logger = logger,
            listener = listener,
        )
    }

    @Test
    fun testShouldSampleBlockedThread() {
        detector.onMonitorThreadInterval(-23409)
        assertEquals(0, listener.intervalCount)

        detector.onMonitorThreadInterval(0)
        assertEquals(0, listener.intervalCount)

        detector.onMonitorThreadInterval(BASELINE_MS)
        assertEquals(0, listener.intervalCount)

        detector.onMonitorThreadInterval(BASELINE_MS - 23409)
        assertEquals(0, listener.intervalCount)

        detector.onMonitorThreadInterval(BASELINE_MS + 50)
        assertEquals(0, listener.intervalCount)

        detector.onMonitorThreadInterval(BASELINE_MS + 1001)
        assertEquals(1, listener.intervalCount)

        detector.onMonitorThreadInterval(BASELINE_MS + 5000)
        assertEquals(2, listener.intervalCount)

        detector.onMonitorThreadInterval(BASELINE_MS + 30000)
        assertEquals(3, listener.intervalCount)
    }

    @Test
    fun testListenerFired() {
        val now = BASELINE_MS + 3000
        clock.setCurrentTime(now)
        detector.onMonitorThreadInterval(BASELINE_MS + 2000)
        assertEquals(1, listener.intervalCount)
    }

    @Test
    fun testSampleBackoff() {
        val now = BASELINE_MS + 2000
        clock.setCurrentTime(now)
        detector.start()
        detector.onMonitorThreadInterval(now + 10)
        assertEquals(0, listener.intervalCount)
    }

    @Test
    fun testStartDoubleCall() {
        detector.start()

        assertEquals(1, watchdogExecutorService.submitCount)
        watchdogExecutorService.runCurrentlyBlocked()
        clock.tick(10L)

        // double-start should not schedule anything
        detector.start()
        watchdogExecutorService.runCurrentlyBlocked()
        assertEquals(1, watchdogExecutorService.submitCount)
    }

    @Test
    fun `starting monitoring thread twice does not result in multiple recurring tasks`() {
        repeat(2) {
            detector.start()
            watchdogExecutorService.runCurrentlyBlocked()
            assertEquals(1, watchdogExecutorService.scheduledTasksCount())
        }
    }
}
