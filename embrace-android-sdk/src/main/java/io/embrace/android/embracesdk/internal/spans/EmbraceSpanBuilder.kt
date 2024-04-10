package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.arch.schema.KeySpan
import io.embrace.android.embracesdk.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

/**
 * Wrapper for the [SpanBuilder] that stores the input data so that they can be accessed
 */
internal class EmbraceSpanBuilder(
    tracer: Tracer,
    name: String,
    telemetryType: TelemetryType,
    internal: Boolean,
    private: Boolean,
    val parent: EmbraceSpan?,
) {
    val fixedAttributes = mutableListOf<FixedAttribute>(telemetryType)
    val spanName = if (internal) {
        name.toEmbraceObjectName()
    } else {
        name
    }

    private val otelSpanBuilder = tracer.spanBuilder(spanName)

    init {
        // If there is a parent, extract the wrapped OTel span and set it as the parent in the wrapped OTel SpanBuilder
        if (parent == null) {
            otelSpanBuilder.setNoParent()
            if (telemetryType == EmbType.Performance.Default) {
                fixedAttributes.add(KeySpan)
            }
        } else if (parent is EmbraceSpanImpl) {
            parent.wrappedSpan()?.let {
                otelSpanBuilder.setParent(Context.current().with(it))
            }
        }

        if (private) {
            fixedAttributes.add(PrivateSpan)
        }
    }

    fun startSpan(startTimeMs: Long): Span {
        val startedSpan = otelSpanBuilder.setStartTimestamp(startTimeMs, TimeUnit.MILLISECONDS).startSpan()
        fixedAttributes.forEach { attribute ->
            startedSpan.setFixedAttribute(attribute)
        }
        return startedSpan
    }
}
