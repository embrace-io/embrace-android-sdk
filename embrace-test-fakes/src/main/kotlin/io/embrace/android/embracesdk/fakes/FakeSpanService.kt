package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanBuilder
import io.embrace.android.embracesdk.internal.spans.PersistableEmbraceSpan
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.context.Context

class FakeSpanService : SpanService {

    val createdSpans: MutableList<FakePersistableEmbraceSpan> = mutableListOf()

    override fun initializeService(sdkInitStartTimeMs: Long) {
    }

    override fun initialized(): Boolean = true

    override fun createSpan(
        name: String,
        autoTerminationMode: AutoTerminationMode,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
    ): PersistableEmbraceSpan = FakePersistableEmbraceSpan(
        name = name,
        parentContext = parent?.run { Context.root().with(parent as PersistableEmbraceSpan) } ?: Context.root(),
        type = type,
        internal = internal,
        private = private,
        autoTerminationMode = autoTerminationMode
    ).apply {
        createdSpans.add(this)
    }

    override fun createSpan(
        embraceSpanBuilder: EmbraceSpanBuilder,
    ): PersistableEmbraceSpan = FakePersistableEmbraceSpan(
        name = embraceSpanBuilder.spanName,
        parentContext = embraceSpanBuilder.parentContext,
        type = embraceSpanBuilder.getFixedAttributes().filterIsInstance<TelemetryType>().single(),
        internal = embraceSpanBuilder.internal,
        private = embraceSpanBuilder.getFixedAttributes().contains(PrivateSpan),
        autoTerminationMode = embraceSpanBuilder.autoTerminationMode,
    ).apply {
        createdSpans.add(this)
    }

    override fun <T> recordSpan(
        name: String,
        autoTerminationMode: AutoTerminationMode,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        code: () -> T,
    ): T {
        return code()
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        autoTerminationMode: AutoTerminationMode,
        parent: EmbraceSpan?,
        type: TelemetryType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ): Boolean {
        createdSpans.add(
            FakePersistableEmbraceSpan(
                name = name,
                parentContext = parent?.run { Context.root().with(parent as PersistableEmbraceSpan) } ?: Context.root(),
                type = type,
                internal = internal,
                private = private
            ).apply {
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
