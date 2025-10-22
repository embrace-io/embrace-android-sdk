package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

class FakeInstrumentationApi(
    private val sdkCurrentTimeMs: Long = 1000
) : InstrumentationApi {
    override fun appReady() {
    }

    override fun activityLoaded(activity: Activity) {
    }

    override fun getSdkCurrentTimeMs(): Long = sdkCurrentTimeMs

    override fun addLoadTraceAttribute(activity: Activity, key: String, value: String) {
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
    }

    override fun addStartupTraceAttribute(key: String, value: String) {
    }

    override fun addStartupTraceChildSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) {
    }
}
