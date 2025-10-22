package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

class FakeInstrumentationApi(
    private val sdkCurrentTimeMs: Long = 1000
) : InstrumentationApi {
    override fun appReady() {
        TODO("Not yet implemented")
    }

    override fun activityLoaded(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun getSdkCurrentTimeMs(): Long = sdkCurrentTimeMs

    override fun addLoadTraceAttribute(activity: Activity, key: String, value: String) {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    override fun addStartupTraceAttribute(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun addStartupTraceChildSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) {
        TODO("Not yet implemented")
    }
}
