package io.embrace.android.embracesdk.internal.anr

import android.os.Looper
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.behavior.FakeAnrBehavior
import io.embrace.android.embracesdk.internal.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.internal.anr.detection.TargetThreadHandler
import io.embrace.android.embracesdk.internal.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
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
    val logger = EmbLoggerImpl()

    lateinit var fakeConfigService: FakeConfigService
    lateinit var fakeProcessStateService: FakeProcessStateService
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

    override fun before() {
        clock.setCurrentTime(0)
        looper = mockk(relaxed = true)
        anrBehavior = FakeAnrBehavior()
        anrMonitorThread = AtomicReference(Thread.currentThread())
        fakeConfigService = FakeConfigService(anrBehavior = anrBehavior)
        fakeProcessStateService = FakeProcessStateService(false)
        anrExecutorService = scheduledExecutorSupplier.invoke()
        state = ThreadMonitoringState(clock)
        worker = BackgroundWorker(anrExecutorService)
        targetThreadHandler = TargetThreadHandler(
            looper = looper,
            anrMonitorWorker = worker,
            clock = clock
        )
        blockedThreadDetector = BlockedThreadDetector(
            configService = fakeConfigService,
            clock = clock,
            state = state,
            targetThread = Thread.currentThread()
        )
        livenessCheckScheduler = LivenessCheckScheduler(
            configService = fakeConfigService,
            anrMonitorWorker = worker,
            clock = clock,
            state = state,
            targetThreadHandler = targetThreadHandler,
            blockedThreadDetector = blockedThreadDetector,
            logger = logger
        )
        stacktraceSampler = AnrStacktraceSampler(
            configService = fakeConfigService,
            clock = clock,
            targetThread = looper.thread,
            anrMonitorWorker = worker
        )
        anrService = EmbraceAnrService(
            configService = fakeConfigService,
            looper = looper,
            logger = logger,
            livenessCheckScheduler = livenessCheckScheduler,
            anrMonitorWorker = worker,
            state = state,
            clock = clock,
            stacktraceSampler = stacktraceSampler,
            processStateService = fakeProcessStateService
        )
    }

    /**
     * Recreates the ANR service. Useful for tests where we need to update the fakes that we pass to the EmbraceAnrService.
     */
    fun recreateService() {
        anrService = EmbraceAnrService(
            configService = fakeConfigService,
            looper = looper,
            logger = logger,
            livenessCheckScheduler = livenessCheckScheduler,
            anrMonitorWorker = worker,
            state = state,
            clock = clock,
            stacktraceSampler = stacktraceSampler,
            processStateService = fakeProcessStateService
        )
    }
}
