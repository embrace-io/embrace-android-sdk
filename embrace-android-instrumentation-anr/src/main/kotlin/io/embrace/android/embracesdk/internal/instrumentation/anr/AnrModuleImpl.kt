package io.embrace.android.embracesdk.internal.instrumentation.anr

import android.os.Looper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.TargetThreadHandler
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.worker.Worker

class AnrModuleImpl(
    args: InstrumentationArgs,
) : AnrModule {

    private val anrMonitorWorker = args.backgroundWorker(Worker.Background.AnrWatchdogWorker)

    override val anrService: AnrService? by lazy {
        if (args.configService.autoDataCaptureBehavior.isAnrCaptureEnabled()) {
            EmbraceAnrService(
                configService = args.configService,
                looper = looper,
                logger = args.logger,
                livenessCheckScheduler = livenessCheckScheduler,
                anrMonitorWorker = anrMonitorWorker,
                state = state,
                clock = args.clock,
                stacktraceSampler = stacktraceSampler,
                appStateTracker = args.appStateTracker,
            )
        } else {
            null
        }
    }

    override val anrOtelMapper: OtelPayloadMapper? by lazy {
        if (args.configService.autoDataCaptureBehavior.isAnrCaptureEnabled()) {
            AnrOtelMapper(
                checkNotNull(anrService),
                args.clock,
                args.destination,
            )
        } else {
            null
        }
    }

    private val looper by lazy { Looper.getMainLooper() }

    private val state by lazy { ThreadMonitoringState(args.clock) }

    private val stacktraceSampler by lazy {
        AnrStacktraceSampler(
            configService = args.configService,
            clock = args.clock,
            targetThread = looper.thread,
            anrMonitorWorker = anrMonitorWorker
        )
    }

    private val targetThreadHandler by lazy {
        TargetThreadHandler(
            looper = looper,
            anrMonitorWorker = anrMonitorWorker,
            clock = args.clock,
        )
    }

    override val blockedThreadDetector by lazy {
        BlockedThreadDetector(
            configService = args.configService,
            clock = args.clock,
            state = state,
            targetThread = looper.thread,
        )
    }

    private val livenessCheckScheduler by lazy {
        LivenessCheckScheduler(
            configService = args.configService,
            anrMonitorWorker = anrMonitorWorker,
            clock = args.clock,
            state = state,
            targetThreadHandler = targetThreadHandler,
            blockedThreadDetector = blockedThreadDetector,
            logger = args.logger,
        )
    }
}
