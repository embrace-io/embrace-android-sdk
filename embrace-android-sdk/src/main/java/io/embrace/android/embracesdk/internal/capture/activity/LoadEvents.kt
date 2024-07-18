package io.embrace.android.embracesdk.internal.capture.activity

internal interface LoadEvents {

    fun create(instanceId: Int, activityName: String, timestampMs: Long)

    fun createEnd(instanceId: Int, timestampMs: Long)

    fun start(instanceId: Int, activityName: String, timestampMs: Long)

    fun startEnd(instanceId: Int, timestampMs: Long)

    fun resume(instanceId: Int, timestampMs: Long)

    fun resumeEnd(instanceId: Int, timestampMs: Long)

    fun firstRender(instanceId: Int, timestampMs: Long)

    fun firstRenderEnd(instanceId: Int, timestampMs: Long)

    enum class OpenType(val typeName: String) {
        COLD("cold"), HOT("hot")
    }

    enum class EndEvent(val eventName: String) {
        RENDER("render"), RESUME("open")
    }
}