package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan

class TraceWriterImpl(private val spanService: SpanService) : TraceWriter {

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
            autoTerminationMode = mode,
            type = schemaType.telemetryType
        )?.apply {
            schemaType.attributes().forEach {
                addAttribute(it.key, it.value)
            }
        } ?: return null
        return SpanTokenImpl(span)
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

    private class SpanTokenImpl(private val span: EmbraceSpan) : SpanToken {
        override fun stop(endTimeMs: Long?) {
            span.stop(endTimeMs = endTimeMs)
        }
    }
}
