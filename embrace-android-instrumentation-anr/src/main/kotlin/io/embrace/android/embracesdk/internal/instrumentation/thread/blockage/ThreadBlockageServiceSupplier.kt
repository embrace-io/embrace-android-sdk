package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import android.os.Looper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.worker.Worker

/**
 * Function that returns an instance of [ThreadBlockageService].
 */
typealias ThreadBlockageServiceSupplier = (args: InstrumentationArgs) -> ThreadBlockageService?

fun createThreadBlockageService(args: InstrumentationArgs): ThreadBlockageService? {
    if (!args.configService.autoDataCaptureBehavior.isThreadBlockageCaptureEnabled()) {
        return null
    }

    val watchdogWorker by lazy { args.backgroundWorker(Worker.Background.ThreadBlockageWatchdogWorker) }
    val looper by lazy { Looper.getMainLooper() }

    val anrBehavior = args.configService.threadBlockageBehavior
    val stacktraceSampler by lazy {
        ThreadBlockageSampler(
            clock = args.clock,
            targetThread = looper.thread,
            maxIntervalsPerSession = anrBehavior.getMaxIntervalsPerSession(),
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
    return ThreadBlockageServiceImpl(
        args = args,
        blockedThreadDetector = blockedThreadDetector,
        watchdogWorker = watchdogWorker,
        stacktraceSampler = stacktraceSampler,
    )
}
