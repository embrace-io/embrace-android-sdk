package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanBuilderWrapper
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.context.Context

class FakeSpanService : SpanService {

    val createdSpans: MutableList<FakeEmbraceSdkSpan> = mutableListOf()

    override fun initializeService(sdkInitStartTimeMs: Long) {
    }

    override fun initialized(): Boolean = true

    override fun createSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSdkSpan = FakeEmbraceSdkSpan(
        name = name,
        parentContext = parent?.run { Context.root().with(parent as EmbraceSdkSpan) } ?: Context.root(),
        type = type,
        internal = internal,
        private = private,
        autoTerminationMode = autoTerminationMode
    ).apply {
        createdSpans.add(this)
    }

    override fun createSpan(
        otelSpanBuilderWrapper: OtelSpanBuilderWrapper,
    ): EmbraceSdkSpan = FakeEmbraceSdkSpan(
        name = otelSpanBuilderWrapper.initialSpanName,
        parentContext = otelSpanBuilderWrapper.getParentContext(),
        type = otelSpanBuilderWrapper.embraceAttributes.filterIsInstance<EmbType>().single(),
        internal = otelSpanBuilderWrapper.internal,
        private = otelSpanBuilderWrapper.embraceAttributes.contains(PrivateSpan),
    ).apply {
        createdSpans.add(this)
    }

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        autoTerminationMode: AutoTerminationMode,
        code: () -> T,
    ): T {
        return code()
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        errorCode: ErrorCode?,
    ): Boolean {
        createdSpans.add(
            FakeEmbraceSdkSpan(
                name = name,
                parentContext = parent?.run { Context.root().with(parent as EmbraceSdkSpan) } ?: Context.root(),
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
