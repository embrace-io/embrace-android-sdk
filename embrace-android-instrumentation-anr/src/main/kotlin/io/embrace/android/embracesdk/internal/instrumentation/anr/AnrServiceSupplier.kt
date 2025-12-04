package io.embrace.android.embracesdk.internal.instrumentation.anr

import android.os.Looper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.worker.Worker

/**
 * Function that returns an instance of [AnrService].
 */
typealias AnrServiceSupplier = (args: InstrumentationArgs) -> AnrService?

fun createAnrService(args: InstrumentationArgs): AnrService? {
    if (!args.configService.autoDataCaptureBehavior.isAnrCaptureEnabled()) {
        return null
    }

    val anrMonitorWorker by lazy { args.backgroundWorker(Worker.Background.AnrWatchdogWorker) }
    val looper by lazy { Looper.getMainLooper() }
    val state by lazy { ThreadMonitoringState(args.clock) }

    val stacktraceSampler by lazy {
        AnrStacktraceSampler(
            clock = args.clock,
            targetThread = looper.thread,
            anrMonitorWorker = anrMonitorWorker,
            maxIntervalsPerSession = args.configService.anrBehavior.getMaxAnrIntervalsPerSession(),
            maxStacktracesPerInterval = args.configService.anrBehavior.getMaxStacktracesPerInterval(),
            stacktraceFrameLimit = args.configService.anrBehavior.getStacktraceFrameLimit(),
        )
    }
    val blockedThreadDetector by lazy {
        BlockedThreadDetector(
            anrMonitorWorker = anrMonitorWorker,
            clock = args.clock,
            state = state,
            looper = looper,
            logger = args.logger,
            intervalMs = args.configService.anrBehavior.getSamplingIntervalMs(),
            blockedDurationThreshold = args.configService.anrBehavior.getMinDuration(),
            listener = stacktraceSampler,
        )
    }
    return EmbraceAnrService(
        args = args,
        blockedThreadDetector = blockedThreadDetector,
        anrMonitorWorker = anrMonitorWorker,
        state = state,
        stacktraceSampler = stacktraceSampler,
    )
}
