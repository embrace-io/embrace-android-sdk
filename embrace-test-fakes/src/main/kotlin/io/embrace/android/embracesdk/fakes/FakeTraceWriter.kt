package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.destination.SpanToken
import io.embrace.android.embracesdk.internal.arch.destination.TraceWriter
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.spans.AutoTerminationMode

class FakeTraceWriter : TraceWriter {

    private val spanService = FakeSpanService()

    val createdSpans: MutableList<FakeEmbraceSdkSpan>
        get() = spanService.createdSpans

    override fun startSpanCapture(
        schemaType: SchemaType,
        startTimeMs: Long,
        autoTerminate: Boolean,
    ): SpanToken? {
        val mode = when {
            autoTerminate -> AutoTerminationMode.ON_BACKGROUND
            else -> AutoTerminationMode.NONE
        }
        val span = spanService.startSpan(
            name = schemaType.fixedObjectName,
            startTimeMs = startTimeMs,
            type = schemaType.telemetryType,
            autoTerminationMode = mode,
        )?.apply {
            schemaType.attributes().forEach {
                addAttribute(it.key, it.value)
            }
        }
        return span?.let(::FakeSpanToken)
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        type: EmbType,
        attributes: Map<String, String>,
    ) {
        spanService.recordCompletedSpan(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            type = type,
            attributes = attributes,
        )
    }
}
