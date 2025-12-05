package io.embrace.android.embracesdk.internal.instrumentation.anr

import android.os.Looper
import io.embrace.android.embracesdk.fakes.FakeAppStateTracker
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.behavior.FakeAnrBehavior
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.every
import io.mockk.mockk
import org.junit.rules.ExternalResource
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference

/**
 * A [org.junit.Rule] that creates an [AnrServiceImpl] suitable for use in tests
 * using mostly real sub-components.
 */
internal class AnrServiceRule<T : ScheduledExecutorService>(
    val clock: FakeClock = FakeClock(),
    private val scheduledExecutorSupplier: Provider<T>,
) : ExternalResource() {
    val logger = FakeEmbLogger()

    lateinit var fakeConfigService: FakeConfigService
    lateinit var fakeAppStateTracker: FakeAppStateTracker
    lateinit var anrService: AnrServiceImpl
    lateinit var blockedThreadDetector: BlockedThreadDetector
    lateinit var anrBehavior: FakeAnrBehavior
    lateinit var watchdogExecutorService: T
    lateinit var watchdogMonitorThread: AtomicReference<Thread>
    lateinit var stacktraceSampler: AnrStacktraceSampler
    lateinit var looper: Looper
    lateinit var worker: BackgroundWorker
    lateinit var args: FakeInstrumentationArgs

    override fun before() {
        clock.setCurrentTime(0)
        looper = mockk(relaxed = true) {
            every { thread } returns Thread.currentThread()
        }
        anrBehavior = FakeAnrBehavior()
        watchdogMonitorThread = AtomicReference(Thread.currentThread())
        fakeConfigService = FakeConfigService(anrBehavior = anrBehavior)
        fakeAppStateTracker = FakeAppStateTracker(AppState.FOREGROUND)
        watchdogExecutorService = scheduledExecutorSupplier.invoke()
        worker = BackgroundWorker(watchdogExecutorService)
        stacktraceSampler = AnrStacktraceSampler(
            clock = clock,
            targetThread = looper.thread,
            maxIntervalsPerSession = fakeConfigService.anrBehavior.getMaxAnrIntervalsPerSession(),
            maxStacktracesPerInterval = fakeConfigService.anrBehavior.getMaxStacktracesPerInterval(),
            stacktraceFrameLimit = fakeConfigService.anrBehavior.getStacktraceFrameLimit(),
        )
        blockedThreadDetector = BlockedThreadDetector(
            watchdogWorker = worker,
            clock = clock,
            looper = looper,
            blockedDurationThreshold = fakeConfigService.anrBehavior.getMinDuration(),
            intervalMs = fakeConfigService.anrBehavior.getSamplingIntervalMs(),
            listener = stacktraceSampler,
            logger = logger,
        )
        args = FakeInstrumentationArgs(
            mockk(),
            configService = fakeConfigService,
            logger = logger,
            clock = clock,
            appStateTracker = fakeAppStateTracker,
        )
        anrService = AnrServiceImpl(
            args = args,
            blockedThreadDetector = blockedThreadDetector,
            watchdogWorker = worker,
            stacktraceSampler = stacktraceSampler,
        )
    }

    /**
     * Recreates the ANR service. Useful for tests where we need to update the fakes that we pass to the EmbraceAnrService.
     */
    fun recreateService() {
        anrService = AnrServiceImpl(
            args = args,
            blockedThreadDetector = blockedThreadDetector,
            watchdogWorker = worker,
            stacktraceSampler = stacktraceSampler,
        )
    }
}
