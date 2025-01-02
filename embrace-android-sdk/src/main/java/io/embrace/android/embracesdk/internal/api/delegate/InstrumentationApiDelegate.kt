package io.embrace.android.embracesdk.internal.api.delegate

import android.app.Activity
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.capture.activity.traceInstanceId
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject

internal class InstrumentationApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : InstrumentationApi {

    private val clock: Clock = bootstrapper.initModule.clock
    private val uiLoadTraceEmitter by embraceImplInject(sdkCallChecker) {
        bootstrapper.dataCaptureServiceModule.uiLoadTraceEmitter
    }

    override fun activityLoaded(activity: Activity) {
        if (sdkCallChecker.check("activity_fully_loaded")) {
            uiLoadTraceEmitter?.complete(traceInstanceId(activity), clock.now())
        }
    }
}
