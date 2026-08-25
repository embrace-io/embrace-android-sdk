package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Application
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes

class LeakDetectionDataSource(args: InstrumentationArgs) :
    DataSourceImpl(
        args,
        limitStrategy = UpToLimitStrategy { MAX_LEAK_DETECTION },
        "leak-detection",
    ),
    SessionPartEndListener {
    companion object {
        /**
         * Caps how many times [captureTelemetry] is allowed to succeed for this data source per session - i.e. how many
         * session parts' worth of [onPreSessionEnd] reports, now that reporting happens once per session part rather
         * than once per leak.
         */
        const val MAX_LEAK_DETECTION = 100
    }

    private val application: Application = args.application

    /**
     * Visible so that tests can drive tracking and reclamation directly, rather than depending on real Activity
     * lifecycle callbacks and real collections.
     */
    internal val leakDetector = LeakDetector(args.clock)

    private val callbacks = ActivityLeakDetectionLifecycleCallbacks(leakDetector, args::activeSessionIds)

    override fun onDataCaptureEnabled() {
        application.registerActivityLifecycleCallbacks(callbacks)
        leakDetector.start()
    }

    override fun onDataCaptureDisabled() {
        application.unregisterActivityLifecycleCallbacks(callbacks)
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
