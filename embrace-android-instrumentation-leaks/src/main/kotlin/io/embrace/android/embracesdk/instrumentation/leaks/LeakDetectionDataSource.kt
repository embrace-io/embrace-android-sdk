package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Application
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

class LeakDetectionDataSource(args: InstrumentationArgs) : DataSourceImpl(
    args,
    limitStrategy = UpToLimitStrategy { MAX_LEAK_DETECTION },
    "leak-detection",
) {
    companion object {
        const val MAX_LEAK_DETECTION = 100
    }

    private val application: Application = args.application

    /**
     * Visible so that tests can drive tracking and reclamation directly, rather than depending on real Activity lifecycle
     * callbacks and real collections.
     */
    internal val leakDetector = LeakDetector(args.clock, ::reportLeak)

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
     * Records a probable leak, timestamped at the end of the lifecycle of [referent] rather than at detection, so that it
     * lands alongside the destroy or close that should have released it.
     *
     * [referent] is read but never retained: only its class name and identity hash code are kept.
     */
    private fun reportLeak(referent: Any, trackedAtMs: Long, token: Any?) {
        val context = token as? LeakContext ?: return
        val sessionIds = context.sessionIds

        if (sessionIds.userSessionId.isEmpty() || sessionIds.sessionPartId.isEmpty()) {
            // Nothing names the session this leak belongs to, and whichever session is current now is not it.
            return
        }

        val className = referent.javaClass.name

        // identityHashCode rather than hashCode: the tracked object may override hashCode, and two distinct leaked
        // instances that compare equal still need to be told apart.
        val identityHashCode = System.identityHashCode(referent)

        captureTelemetry {
            addLog(
                schemaType = SchemaType.MemoryLeak(
                    objectType = context.objectType,
                    className = className,
                    identityHashCode = identityHashCode,
                    userSessionId = sessionIds.userSessionId,
                    sessionPartId = sessionIds.sessionPartId,
                    detectionDelayMs = clock.now() - trackedAtMs,
                ),
                severity = LogSeverity.WARNING,
                message = "Leaked ${context.objectType}: $className",
                timestampMs = trackedAtMs,
            )
        }
    }
}
