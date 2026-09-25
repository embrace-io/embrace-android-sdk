package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import android.os.Looper
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.ThreadBlockageConfig
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.every
import io.mockk.mockk
import org.junit.rules.ExternalResource
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference

/**
 * A [org.junit.Rule] that creates an [ThreadBlockageServiceImpl] suitable for use in tests
 * using mostly real sub-components.
 */
internal class ThreadBlockageServiceRule<T : ScheduledExecutorService>(
    val clock: FakeClock = FakeClock(),
    private val scheduledExecutorSupplier: Provider<T>,
) : ExternalResource() {
    val logger = FakeInternalLogger()

    lateinit var fakeConfigService: FakeConfigService
    lateinit var fakeAppStateTracker: FakeProcessStateTracker
    lateinit var service: ThreadBlockageServiceImpl
    lateinit var blockedThreadDetector: BlockedThreadDetector
    lateinit var config: ThreadBlockageConfig
    lateinit var watchdogExecutorService: T
    lateinit var watchdogMonitorThread: AtomicReference<Thread>
    lateinit var stacktraceSampler: ThreadBlockageSampler
    lateinit var looper: Looper
    lateinit var worker: BackgroundWorker
    lateinit var args: FakeInstrumentationArgs

    override fun before() {
        clock.setCurrentTime(0)
        looper = mockk(relaxed = true) {
            every { thread } returns Thread.currentThread()
        }
        config = ThreadBlockageConfig(sampleIntervalMs = { 5 })
        watchdogMonitorThread = AtomicReference(Thread.currentThread())
        fakeConfigService = FakeConfigService(config = EmbraceConfig(threadBlockage = { config }))
        fakeAppStateTracker = FakeProcessStateTracker(ProcessState.FOREGROUND)
        watchdogExecutorService = scheduledExecutorSupplier.invoke()
        worker = BackgroundWorker(watchdogExecutorService)
        stacktraceSampler = ThreadBlockageSampler(
            clock = clock,
            targetThread = looper.thread,
            maxIntervalsPerSession = config.maxIntervalsPerSession,
            maxSamplesPerInterval = config.maxStacktracesPerInterval,
            stacktraceFrameLimit = config.stacktraceFrameLimit,
        )
        blockedThreadDetector = BlockedThreadDetector(
            watchdogWorker = worker,
            clock = clock,
            looper = looper,
            blockedDurationThreshold = config.minDurationMs,
            intervalMs = config.sampleIntervalMs,
            listener = stacktraceSampler,
            logger = logger,
        )
        args = FakeInstrumentationArgs(
            mockk(),
            configService = fakeConfigService,
            logger = logger,
            clock = clock,
            processStateTracker = fakeAppStateTracker,
        )
        service = ThreadBlockageServiceImpl(
            args = args,
            blockedThreadDetector = blockedThreadDetector,
            watchdogWorker = worker,
            stacktraceSampler = stacktraceSampler,
        )
    }

    /**
     * Recreates the service. Useful for tests where we need to update the fakes that we pass to the EmbraceAnrService.
     */
    fun recreateService() {
        service = ThreadBlockageServiceImpl(
            args = args,
            blockedThreadDetector = blockedThreadDetector,
            watchdogWorker = worker,
            stacktraceSampler = stacktraceSampler,
        )
    }
}
