package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.activity.UiLoadEventListener

class FakeUiLoadEventListener : UiLoadEventListener {
    val events = mutableListOf<EventData>()

    override fun create(instanceId: Int, activityName: String, timestampMs: Long, manualEnd: Boolean) {
        events.add(
            EventData(
                stage = "create",
                instanceId = instanceId,
                activityName = activityName,
                timestampMs = timestampMs
            )
        )
    }

    override fun createEnd(instanceId: Int, timestampMs: Long) {
        events.add(
            EventData(
                stage = "createEnd",
                instanceId = instanceId,
                timestampMs = timestampMs
            )
        )
    }

    override fun start(instanceId: Int, activityName: String, timestampMs: Long, manualEnd: Boolean) {
        events.add(
            EventData(
                stage = "start",
                instanceId = instanceId,
                activityName = activityName,
                timestampMs = timestampMs
            )
        )
    }

    override fun startEnd(instanceId: Int, timestampMs: Long) {
        events.add(
            EventData(
                stage = "startEnd",
                instanceId = instanceId,
                timestampMs = timestampMs
            )
        )
    }

    override fun resume(instanceId: Int, timestampMs: Long) {
        events.add(
            EventData(
                stage = "resume",
                instanceId = instanceId,
                timestampMs = timestampMs
            )
        )
    }

    override fun resumeEnd(instanceId: Int, timestampMs: Long) {
        events.add(
            EventData(
                stage = "resumeEnd",
                instanceId = instanceId,
                timestampMs = timestampMs
            )
        )
    }

    override fun render(instanceId: Int, timestampMs: Long) {
        events.add(
            EventData(
                stage = "render",
                instanceId = instanceId,
                timestampMs = timestampMs
            )
        )
    }

    override fun renderEnd(instanceId: Int, timestampMs: Long) {
        events.add(
            EventData(
                stage = "renderEnd",
                instanceId = instanceId,
                timestampMs = timestampMs
            )
        )
    }

    override fun complete(instanceId: Int, timestampMs: Long) {
        events.add(
            EventData(
                stage = "complete",
                instanceId = instanceId,
                timestampMs = timestampMs
            )
        )
    }

    override fun discard(instanceId: Int, timestampMs: Long) {
        events.add(
            EventData(
                stage = "discard",
                instanceId = instanceId,
                timestampMs = timestampMs
            )
        )
    }

    data class EventData(
        val stage: String,
        val instanceId: Int,
        val activityName: String? = null,
        val timestampMs: Long? = null,
    )
}
