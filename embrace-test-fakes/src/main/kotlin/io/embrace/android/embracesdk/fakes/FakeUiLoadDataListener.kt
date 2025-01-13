package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.activity.UiLoadDataListener
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

class FakeUiLoadDataListener : UiLoadDataListener {
    override fun create(instanceId: Int, activityName: String, timestampMs: Long, manualEnd: Boolean) {
    }

    override fun createEnd(instanceId: Int, timestampMs: Long) {
    }

    override fun start(instanceId: Int, activityName: String, timestampMs: Long, manualEnd: Boolean) {
    }

    override fun startEnd(instanceId: Int, timestampMs: Long) {
    }

    override fun resume(instanceId: Int, timestampMs: Long) {
    }

    override fun resumeEnd(instanceId: Int, timestampMs: Long) {
    }

    override fun render(instanceId: Int, timestampMs: Long) {
    }

    override fun renderEnd(instanceId: Int, timestampMs: Long) {
    }

    override fun complete(instanceId: Int, timestampMs: Long) {
    }

    override fun discard(instanceId: Int, timestampMs: Long) {
    }

    override fun addAttribute(instanceId: Int, key: String, value: String) {
    }

    override fun addChildSpan(
        instanceId: Int,
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ) {
    }
}
