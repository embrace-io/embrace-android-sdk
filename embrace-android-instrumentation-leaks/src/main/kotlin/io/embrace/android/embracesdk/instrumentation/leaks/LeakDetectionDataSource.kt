package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes

class LeakDetectionDataSource(args: InstrumentationArgs) :
    DataSourceImpl(
        args,
        limitStrategy = NoopLimitStrategy,
        "leak-detection",
    ),
    SessionPartEndListener {

    /**
     * Visible so that tests can drive tracking and reclamation directly, rather than depending on real Activity
     * lifecycle callbacks and real collections.
     */
    internal val leakDetector = LeakDetector(args.clock, args.errorHandler)

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
        webViewLeakDetectionEnabled,
    )

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
     * Encodes every currently-tracked suspect into one session attribute, back-attributed to whichever session part
     * originally tracked each one closed - see [encodeLeakSuspects]. Nothing is written when there is nothing to report,
     * rather than setting an empty attribute on every session part.
     */
    override fun onPreSessionEnd() {
        val encoded = encodeLeakSuspects(leakDetector.suspects())
        if (encoded.isNotEmpty()) {
            captureTelemetry {
                addSessionPartAttribute(EmbSessionAttributes.EMB_MEMORY_LEAK_SUSPECTS, encoded)
            }
        }
    }
}
