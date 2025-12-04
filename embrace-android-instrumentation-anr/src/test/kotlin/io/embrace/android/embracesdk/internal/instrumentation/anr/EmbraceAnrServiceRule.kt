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
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.TargetThreadHandler
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.mockk
import org.junit.rules.ExternalResource
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference

/**
 * A [org.junit.Rule] that creates an [EmbraceAnrService] suitable for use in tests using mostly real sub-components including:
 * - [TargetThreadHandler]
 * - [BlockedThreadDetector]
 * - [LivenessCheckScheduler]
 */
internal class EmbraceAnrServiceRule<T : ScheduledExecutorService>(
    val clock: FakeClock = FakeClock(),
    private val scheduledExecutorSupplier: Provider<T>,
) : ExternalResource() {
    val logger = FakeEmbLogger()

    lateinit var fakeConfigService: FakeConfigService
    lateinit var fakeAppStateTracker: FakeAppStateTracker
    lateinit var anrService: EmbraceAnrService
    lateinit var livenessCheckScheduler: LivenessCheckScheduler
    lateinit var state: ThreadMonitoringState
    lateinit var blockedThreadDetector: BlockedThreadDetector
    lateinit var anrBehavior: FakeAnrBehavior
    lateinit var anrExecutorService: T
    lateinit var targetThreadHandler: TargetThreadHandler
    lateinit var anrMonitorThread: AtomicReference<Thread>
    lateinit var stacktraceSampler: AnrStacktraceSampler
    lateinit var looper: Looper
    lateinit var worker: BackgroundWorker
    lateinit var args: FakeInstrumentationArgs

    override fun before() {
        clock.setCurrentTime(0)
        looper = mockk(relaxed = true)
        anrBehavior = FakeAnrBehavior()
        anrMonitorThread = AtomicReference(Thread.currentThread())
        fakeConfigService = FakeConfigService(anrBehavior = anrBehavior)
        fakeAppStateTracker = FakeAppStateTracker(AppState.FOREGROUND)
        anrExecutorService = scheduledExecutorSupplier.invoke()
        state = ThreadMonitoringState(clock)
        worker = BackgroundWorker(anrExecutorService)
        targetThreadHandler = TargetThreadHandler(
            looper = looper,
            anrMonitorWorker = worker,
            clock = clock
        )
        blockedThreadDetector = BlockedThreadDetector(
            clock = clock,
            state = state,
            targetThread = Thread.currentThread(),
            blockedDurationThreshold = fakeConfigService.anrBehavior.getMinDuration(),
            samplingIntervalMs = fakeConfigService.anrBehavior.getSamplingIntervalMs()
        )
        livenessCheckScheduler = LivenessCheckScheduler(
            anrMonitorWorker = worker,
            clock = clock,
            state = state,
            targetThreadHandler = targetThreadHandler,
            blockedThreadDetector = blockedThreadDetector,
            logger = logger,
            intervalMs = fakeConfigService.anrBehavior.getSamplingIntervalMs(),
        )
        stacktraceSampler = AnrStacktraceSampler(
            clock = clock,
            targetThread = looper.thread,
            anrMonitorWorker = worker,
            maxIntervalsPerSession = fakeConfigService.anrBehavior.getMaxAnrIntervalsPerSession(),
            maxStacktracesPerInterval = fakeConfigService.anrBehavior.getMaxStacktracesPerInterval(),
            stacktraceFrameLimit = fakeConfigService.anrBehavior.getStacktraceFrameLimit(),
        )
        args = FakeInstrumentationArgs(
            mockk(),
            configService = fakeConfigService,
            logger = logger,
            clock = clock,
            appStateTracker = fakeAppStateTracker,
        )
        anrService = EmbraceAnrService(
            args = args,
            livenessCheckScheduler = livenessCheckScheduler,
            anrMonitorWorker = worker,
            state = state,
            stacktraceSampler = stacktraceSampler,
        )
    }

    /**
     * Recreates the ANR service. Useful for tests where we need to update the fakes that we pass to the EmbraceAnrService.
     */
    fun recreateService() {
        anrService = EmbraceAnrService(
            args = args,
            livenessCheckScheduler = livenessCheckScheduler,
            anrMonitorWorker = worker,
            state = state,
            stacktraceSampler = stacktraceSampler,
        )
    }
}
