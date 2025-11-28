package io.embrace.android.embracesdk.internal.instrumentation.anr

import android.os.Looper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.TargetThreadHandler
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.worker.Worker

class AnrModuleImpl(args: InstrumentationArgs) : AnrModule {

    private val anrMonitorWorker by lazy { args.backgroundWorker(Worker.Background.AnrWatchdogWorker) }

    override val anrService: AnrService? by lazy {
        if (args.configService.autoDataCaptureBehavior.isAnrCaptureEnabled()) {
            EmbraceAnrService(
                args = args,
                looper = looper,
                livenessCheckScheduler = livenessCheckScheduler,
                anrMonitorWorker = anrMonitorWorker,
                state = state,
                stacktraceSampler = stacktraceSampler,
            )
        } else {
            null
        }
    }

    private val looper by lazy { Looper.getMainLooper() }

    private val state by lazy { ThreadMonitoringState(args.clock) }

    private val stacktraceSampler by lazy {
        AnrStacktraceSampler(
            clock = args.clock,
            targetThread = looper.thread,
            anrMonitorWorker = anrMonitorWorker,
            maxIntervalsPerSession = args.configService.anrBehavior.getMaxAnrIntervalsPerSession(),
            maxStacktracesPerInterval = args.configService.anrBehavior.getMaxStacktracesPerInterval(),
            stacktraceFrameLimit = args.configService.anrBehavior.getStacktraceFrameLimit(),
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
            clock = args.clock,
            state = state,
            targetThread = looper.thread,
            blockedDurationThreshold = args.configService.anrBehavior.getMinDuration(),
            samplingIntervalMs = args.configService.anrBehavior.getSamplingIntervalMs(),
        )
    }

    private val livenessCheckScheduler by lazy {
        LivenessCheckScheduler(
            anrMonitorWorker = anrMonitorWorker,
            clock = args.clock,
            state = state,
            targetThreadHandler = targetThreadHandler,
            blockedThreadDetector = blockedThreadDetector,
            intervalMs = args.configService.anrBehavior.getSamplingIntervalMs(),
            logger = args.logger,
        )
    }
}
