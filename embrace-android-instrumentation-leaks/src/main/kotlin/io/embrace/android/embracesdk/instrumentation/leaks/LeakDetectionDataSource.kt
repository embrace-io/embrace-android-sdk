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

    private val callbacks = ActivityLeakDetectionLifecycleCallbacks()

    override fun onDataCaptureEnabled() {
        application.registerActivityLifecycleCallbacks(callbacks)
    }

    override fun onDataCaptureDisabled() {
        application.unregisterActivityLifecycleCallbacks(callbacks)
    }
}
