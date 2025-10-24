package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi

@OptIn(ExperimentalApi::class)
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
        parentContext = fakeOpenTelemetry().contextFactory.root(),
        type = type,
        internal = internal,
        private = private,
        autoTerminationMode = autoTerminationMode
    ).apply {
        createdSpans.add(this)
    }

    override fun createSpan(
        otelSpanStartArgs: OtelSpanStartArgs,
    ): EmbraceSdkSpan {
        return FakeEmbraceSdkSpan(
            name = otelSpanStartArgs.initialSpanName,
            parentContext = otelSpanStartArgs.parentContext,
            type = otelSpanStartArgs.embraceAttributes.filterIsInstance<EmbType>().single(),
            internal = otelSpanStartArgs.internal,
            private = otelSpanStartArgs.embraceAttributes.contains(PrivateSpan),
            autoTerminationMode = otelSpanStartArgs.autoTerminationMode,
            otelSpanStartArgs = otelSpanStartArgs
        ).apply {
            createdSpans.add(this)
        }
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
                parentContext = fakeOpenTelemetry().contextFactory.root(),
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
