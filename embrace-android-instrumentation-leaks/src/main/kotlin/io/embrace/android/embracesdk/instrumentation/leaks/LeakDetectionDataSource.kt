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
     * Encodes the GC cycles every currently-tracked suspect survived since the previous report into one session attribute,
     * back-attributed to whichever session part originally tracked each one closed - see [encodeLeakSuspects]. Nothing is
     * written when there is nothing to report, rather than setting an empty attribute on every session part.
     */
    override fun onPreSessionEnd() {
        // read once, so that no collection falls between this report and the baseline for the next one
        val cycleCount = leakDetector.currentGcCycleCount()
        val encoded = encodeLeakSuspects(leakDetector.suspects(previousReportedCycleCount, cycleCount))
        previousReportedCycleCount = cycleCount
        if (encoded.isNotEmpty()) {
            captureTelemetry {
                addSessionPartAttribute(EmbSessionAttributes.EMB_MEMORY_LEAK_SUSPECTS, encoded)
            }
        }
    }
}
