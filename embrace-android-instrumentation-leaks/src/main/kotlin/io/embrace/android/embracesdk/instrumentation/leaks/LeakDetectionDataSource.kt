package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes

class LeakDetectionDataSource internal constructor(
    args: InstrumentationArgs,
    /**
     * Visible so that tests can drive tracking and reclamation directly, rather than depending on real Activity
     * lifecycle callbacks and real collections, and supplied by them to control the GC count it reads.
     */
    internal val leakDetector: LeakDetector,
) : DataSourceImpl(
    args,
    limitStrategy = NoopLimitStrategy,
    "leak-detection",
),
    SessionPartEndListener {

    constructor(args: InstrumentationArgs) :
        this(args, LeakDetector(args.clock, args.errorHandler, args.versionChecker))

    private val webViewLeakDetectionEnabled = args.configService.config.autoDataCapture.webViewLeakDetectionEnabled

    private val fragmentSupport: FragmentSupport =
        if (args.configService.config.autoDataCapture.fragmentLeakDetectionEnabled) {
            createFragmentSupport(leakDetector, args::activeSessionIds, args.errorHandler, webViewLeakDetectionEnabled)
        } else {
            NoOpFragmentSupport
        }

    private val callbacks = ActivityLeakDetectionLifecycleCallbacks(
        leakDetector,
        args::activeSessionIds,
        fragmentSupport,
        args.eventBus,
        args.errorHandler,
        args.versionChecker,
        webViewLeakDetectionEnabled,
    )

    private var previousReportedCycleCount = 0L

    override fun onDataCaptureEnabled() {
        callbacks.subscribe()
        leakDetector.start()
    }

    override fun onDataCaptureDisabled() {
        callbacks.close()
        fragmentSupport.close()
        leakDetector.stop()
    }

    /**
     * Writes the GC cycles every currently-tracked suspect survived since the previous report as one session attribute,
     * and the close times of those confirmed since then as another - see [encodeLeakSuspects] and [encodeLeakCandidates].
     * Both are back-attributed to whichever session part originally tracked each suspect closed. Nothing is written for an
     * attribute with nothing to report, rather than setting an empty one on every session part.
     */
    override fun onPreSessionEnd() {
        // read once, so that no collection falls between this report and the baseline for the next one
        val cycleCount = leakDetector.currentGcCycleCount()
        val suspects = leakDetector.suspects(previousReportedCycleCount, cycleCount)
        previousReportedCycleCount = cycleCount
        addIfNotEmpty(EmbSessionAttributes.EMB_MEMORY_LEAK_SUSPECTS, encodeLeakSuspects(suspects))
        val candidates = encodeLeakCandidates(suspects, clock.now(), leakDetector.takeDropped())
        addIfNotEmpty(EmbSessionAttributes.EMB_MEMORY_LEAK_CANDIDATES, candidates)
    }

    private fun addIfNotEmpty(key: String, value: String) {
        if (value.isNotEmpty()) {
            captureTelemetry {
                addSessionPartAttribute(key, value)
            }
        }
    }
}
