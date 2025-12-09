package io.embrace.android.embracesdk.internal.instrumentation.anr

import android.os.Looper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.worker.Worker

/**
 * Function that returns an instance of [AnrService].
 */
typealias AnrServiceSupplier = (args: InstrumentationArgs) -> AnrService?

fun createAnrService(args: InstrumentationArgs): AnrService? {
    if (!args.configService.autoDataCaptureBehavior.isAnrCaptureEnabled()) {
        return null
    }

    val watchdogWorker by lazy { args.backgroundWorker(Worker.Background.AnrWatchdogWorker) }
    val looper by lazy { Looper.getMainLooper() }

    val anrBehavior = args.configService.anrBehavior
    val stacktraceSampler by lazy {
        ThreadBlockageSampler(
            clock = args.clock,
            targetThread = looper.thread,
            maxIntervalsPerSession = anrBehavior.getMaxAnrIntervalsPerSession(),
            maxSamplesPerInterval = anrBehavior.getMaxStacktracesPerInterval(),
            stacktraceFrameLimit = anrBehavior.getStacktraceFrameLimit(),
        )
    }
    val blockedThreadDetector by lazy {
        BlockedThreadDetector(
            watchdogWorker = watchdogWorker,
            clock = args.clock,
            looper = looper,
            logger = args.logger,
            intervalMs = anrBehavior.getSamplingIntervalMs(),
            blockedDurationThreshold = anrBehavior.getMinDuration(),
            listener = stacktraceSampler,
        )
    }
    return AnrServiceImpl(
        args = args,
        blockedThreadDetector = blockedThreadDetector,
        watchdogWorker = watchdogWorker,
        stacktraceSampler = stacktraceSampler,
    )
}
