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

    // TODO: query leakDetector.suspects() (e.g. from a SessionPartEndListener.onPreSessionEnd()) and encode the dataset into
    // a session attribute. Each entry already carries how many GC cycles it has survived since being confirmed.
    private val leakDetector = LeakDetector(args.clock)

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
