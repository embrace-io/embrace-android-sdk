package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import android.os.Looper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.worker.Worker

/**
 * Function that returns an instance of [ThreadBlockageService].
 */
typealias ThreadBlockageServiceSupplier = (args: InstrumentationArgs) -> ThreadBlockageService?

fun createThreadBlockageService(args: InstrumentationArgs): ThreadBlockageService? {
    val autoDataCaptureBehavior = args.configService.autoDataCaptureBehavior
    val threadBlockageCaptureEnabled = autoDataCaptureBehavior.isThreadBlockageCaptureEnabled()

    // One detector serves both features, so either one wanting it is reason enough to run it. Gating on
    // thread blockage capture alone would leave the responsiveness vital silently dead wherever the
    // thread blockage rollout excludes a device, which is invisible in the resulting data.
    if (!threadBlockageCaptureEnabled && !autoDataCaptureBehavior.isResponsivenessCaptureEnabled()) {
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
        )
    }

    // The stacktrace sampler is the first listener, and only when thread blockage capture is enabled.
    // Left unregistered it is never told about a blockage, so the service reports no intervals.
    if (threadBlockageCaptureEnabled) {
        blockedThreadDetector.addListener(stacktraceSampler)
    }
    return ThreadBlockageServiceImpl(
        args = args,
        blockedThreadDetector = blockedThreadDetector,
        watchdogWorker = watchdogWorker,
        stacktraceSampler = stacktraceSampler,
    )
}
