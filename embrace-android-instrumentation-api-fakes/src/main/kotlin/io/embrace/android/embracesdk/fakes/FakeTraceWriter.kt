package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.destination.SpanToken
import io.embrace.android.embracesdk.internal.arch.destination.TraceWriter
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

class FakeTraceWriter : TraceWriter {

    val createdSpans: MutableList<FakeSpanToken> = mutableListOf()

    override fun startSpanCapture(
        schemaType: SchemaType,
        startTimeMs: Long,
        autoTerminate: Boolean,
    ): SpanToken? {
        val token = FakeSpanToken(
            schemaType.fixedObjectName,
            startTimeMs,
            null,
            schemaType.telemetryType,
            schemaType.attributes() + mapOf(schemaType.telemetryType.asPair()),
        )

        createdSpans.add(token)
        return token
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        type: EmbType,
        attributes: Map<String, String>,
    ) {
        val token = FakeSpanToken(
            name,
            startTimeMs,
            null,
            type,
            mapOf(type.asPair()),
        )
        createdSpans.add(token)
    }
}
