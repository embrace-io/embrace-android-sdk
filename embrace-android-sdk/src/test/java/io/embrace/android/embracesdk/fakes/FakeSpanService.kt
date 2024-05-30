package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanBuilder
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan

internal class FakeSpanService : SpanService {

    val createdSpans = mutableListOf<FakePersistableEmbraceSpan>()

    override fun initializeService(sdkInitStartTimeMs: Long) {
    }

    override fun initialized(): Boolean = true

    override fun createSpan(
        name: String,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean
    ): PersistableEmbraceSpan = FakePersistableEmbraceSpan(null, name, type, internal, private).apply {
        createdSpans.add(this)
    }

    override fun createSpan(embraceSpanBuilder: EmbraceSpanBuilder): EmbraceSpan? = null

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        code: () -> T
    ): T {
        return code()
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?
    ): Boolean {
        createdSpans.add(
            FakePersistableEmbraceSpan(parent, name, type, internal, private).apply {
                start(startTimeMs)
                attributes.forEach { (key, value) -> addAttribute(key, value) }
                events.forEach {
                    addEvent(it.name, it.timestampNanos, it.attributes)
                }
                stop(errorCode, endTimeMs)
            }
        )
        return true
    }

    override fun getSpan(spanId: String): EmbraceSpan? = null
}
