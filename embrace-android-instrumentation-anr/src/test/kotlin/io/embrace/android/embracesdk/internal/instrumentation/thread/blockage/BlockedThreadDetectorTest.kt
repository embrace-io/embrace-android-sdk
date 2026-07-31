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
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
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
                    threadBlockageRemoteConfig = cfg,
                ),
            ),
        )
        watchdogExecutorService = BlockingScheduledExecutorService(clock)
        logger = FakeInternalLogger()
        looper = mockk {
            every { thread } returns Thread.currentThread()
        }
        listener = FakeThreadBlockageListener()
        detector = createDetector(logger).apply { addListener(listener) }
    }

    private fun createDetector(logger: InternalLogger): BlockedThreadDetector = BlockedThreadDetector(
        watchdogWorker = BackgroundWorker(watchdogExecutorService),
        clock = clock,
        looper = mockk {
            every { thread } returns Thread.currentThread()
        },
        blockedDurationThreshold = configService.threadBlockageBehavior.getMinDuration(),
        intervalMs = configService.threadBlockageBehavior.getSamplingIntervalMs(),
        logger = logger,
    )

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
    fun `reported blockage starts when the thread was last responsive, not when it was detected`() {
        detector.onTargetThreadProcessedMessage(BASELINE_MS)
        detector.onMonitorThreadInterval(BASELINE_MS + 1500)

        val blockage = listener.started.single()
        assertEquals(BASELINE_MS, blockage.startTimeMs)
        assertEquals(BASELINE_MS + 1500, blockage.lastKnownTimeMs)
        assertEquals(1500, blockage.durationMs)
    }

    @Test
    fun `reported blockage carries the detection parameters in force`() {
        detector.onTargetThreadProcessedMessage(BASELINE_MS)
        detector.onMonitorThreadInterval(BASELINE_MS + 1500)

        val blockage = listener.started.single()
        assertEquals(configService.threadBlockageBehavior.getMinDuration(), blockage.thresholdMs)
        assertEquals(configService.threadBlockageBehavior.getSamplingIntervalMs(), blockage.pollIntervalMs)
    }

    @Test
    fun `last known time advances while blocked and is final once the thread responds`() {
        detector.onTargetThreadProcessedMessage(BASELINE_MS)
        detector.onMonitorThreadInterval(BASELINE_MS + 1500)
        detector.onMonitorThreadInterval(BASELINE_MS + 2000)
        detector.onTargetThreadProcessedMessage(BASELINE_MS + 2500)

        // every callback describes the same blockage, with a later last known time each time
        assertEquals(
            listOf(BASELINE_MS + 1500, BASELINE_MS + 2000),
            listener.ongoing.map { it.lastKnownTimeMs },
        )
        listener.ongoing.forEach { assertEquals(BASELINE_MS, it.startTimeMs) }

        val ended = listener.ended.single()
        assertEquals(BASELINE_MS, ended.startTimeMs)
        assertEquals(BASELINE_MS + 2500, ended.lastKnownTimeMs)
        assertEquals(2500, ended.durationMs)
    }

    @Test
    fun `a second blockage does not report the first blockage's start time`() {
        detector.onTargetThreadProcessedMessage(BASELINE_MS)
        detector.onMonitorThreadInterval(BASELINE_MS + 1500)
        detector.onTargetThreadProcessedMessage(BASELINE_MS + 2000)

        detector.onMonitorThreadInterval(BASELINE_MS + 3500)
        detector.onTargetThreadProcessedMessage(BASELINE_MS + 4500)

        assertEquals(listOf(BASELINE_MS, BASELINE_MS + 2000), listener.started.map { it.startTimeMs })
        // each blockage runs from the previous response to the next one: 0 -> 2000, 2000 -> 4500
        assertEquals(listOf(2000L, 2500L), listener.ended.map { it.durationMs })
    }

    @Test
    fun `no blockage is reported when the thread responds within the threshold`() {
        detector.onTargetThreadProcessedMessage(BASELINE_MS)
        detector.onMonitorThreadInterval(BASELINE_MS + 500)
        detector.onTargetThreadProcessedMessage(BASELINE_MS + 900)

        assertEquals(0, listener.started.size)
        assertEquals(0, listener.ended.size)
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

    @Test
    fun `every registered listener receives every callback`() {
        val second = FakeThreadBlockageListener()
        detector.addListener(second)

        detector.onTargetThreadProcessedMessage(BASELINE_MS)
        detector.onMonitorThreadInterval(BASELINE_MS + 1500)
        detector.onMonitorThreadInterval(BASELINE_MS + 2000)
        detector.onTargetThreadProcessedMessage(BASELINE_MS + 2500)

        listOf(listener, second).forEach { registered ->
            assertEquals(BASELINE_MS, registered.started.single().startTimeMs)
            assertEquals(
                listOf(BASELINE_MS + 1500, BASELINE_MS + 2000),
                registered.ongoing.map { it.lastKnownTimeMs },
            )
            assertEquals(2500L, registered.ended.single().durationMs)
        }
    }

    @Test
    fun `registering the same listener twice delivers one callback`() {
        detector.addListener(listener)

        detector.onTargetThreadProcessedMessage(BASELINE_MS)
        detector.onMonitorThreadInterval(BASELINE_MS + 1500)

        assertEquals(1, listener.started.size)
    }

    @Test
    fun `a removed listener stops receiving callbacks`() {
        val second = FakeThreadBlockageListener()
        detector.addListener(second)

        detector.onTargetThreadProcessedMessage(BASELINE_MS)
        detector.onMonitorThreadInterval(BASELINE_MS + 1500)
        detector.removeListener(second)
        detector.onTargetThreadProcessedMessage(BASELINE_MS + 2500)

        // removed mid-blockage: it saw the start but not the end, while the other listener saw both
        assertEquals(1, second.started.size)
        assertEquals(0, second.ended.size)
        assertEquals(1, listener.ended.size)
    }

    @Test
    fun `a listener that throws does not stop the others being told`() {
        val errorLogger = FakeInternalLogger(throwOnInternalError = false)
        val isolated = createDetector(errorLogger)
        val survivor = FakeThreadBlockageListener()
        isolated.addListener(ThrowingThreadBlockageListener())
        isolated.addListener(survivor)

        isolated.onTargetThreadProcessedMessage(BASELINE_MS)
        isolated.onMonitorThreadInterval(BASELINE_MS + 1500)
        isolated.onTargetThreadProcessedMessage(BASELINE_MS + 2500)

        assertEquals(BASELINE_MS, survivor.started.single().startTimeMs)
        assertEquals(2500L, survivor.ended.single().durationMs)

        // every failure is reported, and nothing else is
        assertEquals(
            listOf(InternalErrorType.ThreadBlockageListenerFail.toString()),
            errorLogger.internalErrorMessages.map { it.msg }.distinct(),
        )
    }

    private class ThrowingThreadBlockageListener : ThreadBlockageListener {
        override fun onBlockageStart(blockage: ThreadBlockage): Unit = error("listener failure")
        override fun onBlockageOngoing(blockage: ThreadBlockage): Unit = error("listener failure")
        override fun onBlockageEnd(blockage: ThreadBlockage): Unit = error("listener failure")
    }
}
