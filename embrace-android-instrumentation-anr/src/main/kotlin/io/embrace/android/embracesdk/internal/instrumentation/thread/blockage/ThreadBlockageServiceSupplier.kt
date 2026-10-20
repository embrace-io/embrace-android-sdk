package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import android.os.Looper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.worker.Worker

fun createThreadBlockageService(args: InstrumentationArgs): ThreadBlockageService? {
    val configService = args.configService
    if (!configService.config.autoDataCapture.threadBlockageCaptureEnabled ||
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
            errorHandler = args.errorHandler,
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
    ).apply { scheduleStartupBackgroundCheck() }
}
