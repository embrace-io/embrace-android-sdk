package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Application
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy

class LeakDetectionDataSource(args: InstrumentationArgs) : DataSourceImpl(
    args,
    limitStrategy = UpToLimitStrategy { MAX_LEAK_DETECTION },
    "leak-detection",
) {
    companion object {
        const val MAX_LEAK_DETECTION = 100
    }

    private val application: Application = args.application

    private val leakDetector = LeakDetector(args.clock) { _, _, _ ->
        // TODO: Send the leak data somewhere, and do something with it.
        // In basic cases we want to capture *when* the leak actually took place so that we can back-date the leak event to the
        // "destroy"/"close" operation that should have led directly to the release of the reference.
    }

    private val callbacks = ActivityLeakDetectionLifecycleCallbacks(leakDetector, args::activeSessionIds)

    override fun onDataCaptureEnabled() {
        application.registerActivityLifecycleCallbacks(callbacks)
        leakDetector.start()
    }

    override fun onDataCaptureDisabled() {
        application.unregisterActivityLifecycleCallbacks(callbacks)
        leakDetector.stop()
    }
}
