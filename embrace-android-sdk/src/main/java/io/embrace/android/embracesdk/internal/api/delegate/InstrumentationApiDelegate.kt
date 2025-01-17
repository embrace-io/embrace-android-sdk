package io.embrace.android.embracesdk.internal.api.delegate

import android.app.Activity
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.capture.activity.traceInstanceId
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

internal class InstrumentationApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : InstrumentationApi {

    private val clock: Clock = bootstrapper.initModule.clock
    private val uiLoadTraceEmitter by embraceImplInject(sdkCallChecker) {
        bootstrapper.dataCaptureServiceModule.uiLoadDataListener
    }
    private val appStartupDataCollector by embraceImplInject(sdkCallChecker) {
        bootstrapper.dataCaptureServiceModule.appStartupDataCollector
    }

    override fun activityLoaded(activity: Activity) {
        if (sdkCallChecker.check("activity_fully_loaded")) {
            uiLoadTraceEmitter?.complete(traceInstanceId(activity), clock.now())
        }
    }

    override fun getSdkCurrentTimeMs(): Long = clock.now()

    override fun addLoadTraceAttribute(activity: Activity, key: String, value: String) {
        if (sdkCallChecker.check("add_load_trace_attribute")) {
            uiLoadTraceEmitter?.addAttribute(traceInstanceId(activity), key, value)
        }
    }

    override fun addLoadTraceChildSpan(
        activity: Activity,
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) {
        if (sdkCallChecker.check("add_load_trace_child_span")) {
            uiLoadTraceEmitter?.addChildSpan(
                instanceId = traceInstanceId(activity),
                name = name,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                attributes = attributes,
                events = events,
                errorCode = errorCode
            )
        }
    }

    override fun addStartupTraceAttribute(key: String, value: String) {
        if (sdkCallChecker.check("add_startup_trace_attribute")) {
            appStartupDataCollector?.addAttribute(key, value)
        }
    }

    override fun addStartupTraceChildSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) {
        if (sdkCallChecker.check("add_startup_trace_child_span")) {
            appStartupDataCollector?.addTrackedInterval(
                name = name,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                attributes = attributes,
                events = events,
                errorCode = errorCode
            )
        }
    }
}
