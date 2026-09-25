package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import android.os.Looper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.worker.Worker

/**
 * Function that returns an instance of [ThreadBlockageService].
 */
typealias ThreadBlockageServiceSupplier = (args: InstrumentationArgs) -> ThreadBlockageService?

fun createThreadBlockageService(args: InstrumentationArgs): ThreadBlockageService? {
    val configService = args.configService
    if (!configService.autoDataCaptureBehavior.isThreadBlockageCaptureEnabled() ||
        !configService.config.threadBlockage.captureEnabled
    ) {
        return null
    }

    val watchdogWorker by lazy { args.backgroundWorker(Worker.Background.ThreadBlockageWatchdogWorker) }
    val looper by lazy { Looper.getMainLooper() }

    val cfg = configService.config.threadBlockage
    val stacktraceSampler by lazy {
        ThreadBlockageSampler(
            clock = args.clock,
            targetThread = looper.thread,
            maxIntervalsPerSession = cfg.maxIntervalsPerSession,
            maxSamplesPerInterval = cfg.maxStacktracesPerInterval,
            stacktraceFrameLimit = cfg.stacktraceFrameLimit,
        )
    }
    val blockedThreadDetector by lazy {
        BlockedThreadDetector(
            watchdogWorker = watchdogWorker,
            clock = args.clock,
            looper = looper,
            logger = args.logger,
            intervalMs = cfg.sampleIntervalMs,
            blockedDurationThreshold = cfg.minDurationMs,
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
