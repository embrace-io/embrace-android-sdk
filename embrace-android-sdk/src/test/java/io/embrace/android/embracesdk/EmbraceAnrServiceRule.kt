package io.embrace.android.embracesdk

import android.os.Looper
import io.embrace.android.embracesdk.anr.EmbraceAnrService
import io.embrace.android.embracesdk.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.anr.detection.TargetThreadHandler
import io.embrace.android.embracesdk.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.anr.sigquit.SigquitDetectionService
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.mockk.every
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
    private val scheduledExecutorSupplier: () -> T
) : ExternalResource() {
    val logger = InternalEmbraceLogger()
    val mockSigquitDetectionService: SigquitDetectionService = mockk(relaxed = true)

    lateinit var fakeConfigService: FakeConfigService
    lateinit var anrService: EmbraceAnrService
    lateinit var livenessCheckScheduler: LivenessCheckScheduler
    lateinit var state: ThreadMonitoringState
    lateinit var blockedThreadDetector: BlockedThreadDetector
    lateinit var cfg: AnrRemoteConfig
    lateinit var anrExecutorService: T
    lateinit var targetThreadHandler: TargetThreadHandler
    lateinit var anrMonitorThread: AtomicReference<Thread>

    override fun before() {
        clock.setCurrentTime(0)
        val mockLooper: Looper = mockk(relaxed = true)
        every { mockLooper.thread } returns Thread.currentThread()
        cfg = AnrRemoteConfig()
        anrMonitorThread = AtomicReference(Thread.currentThread())
        fakeConfigService = FakeConfigService(anrBehavior = fakeAnrBehavior { cfg })
        anrExecutorService = scheduledExecutorSupplier.invoke()
        state = ThreadMonitoringState(clock)
        targetThreadHandler = TargetThreadHandler(
            looper = mockLooper,
            anrExecutorService = anrExecutorService,
            anrMonitorThread = anrMonitorThread,
            configService = fakeConfigService,
            clock = clock
        )
        blockedThreadDetector = BlockedThreadDetector(
            configService = fakeConfigService,
            clock = clock,
            state = state,
            targetThread = Thread.currentThread(),
            anrMonitorThread = anrMonitorThread
        )
        livenessCheckScheduler = LivenessCheckScheduler(
            configService = fakeConfigService,
            anrExecutor = anrExecutorService,
            clock = clock,
            state = state,
            targetThreadHandler = targetThreadHandler,
            blockedThreadDetector = blockedThreadDetector,
            logger = logger,
            anrMonitorThread = anrMonitorThread
        )
        anrService = EmbraceAnrService(
            configService = fakeConfigService,
            looper = mockLooper,
            logger = logger,
            sigquitDetectionService = mockSigquitDetectionService,
            livenessCheckScheduler = livenessCheckScheduler,
            anrExecutorService = anrExecutorService,
            state = state,
            clock = clock,
            anrMonitorThread = anrMonitorThread
        )
    }
}
