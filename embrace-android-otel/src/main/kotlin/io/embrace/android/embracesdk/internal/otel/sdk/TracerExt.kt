package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.schema.TelemetryType
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanBuilderWrapper
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

/**
 * Factory method for the wrapper for the OTel SpanBuilder
 */
fun Tracer.otelSpanBuilderWrapper(
    name: String,
    type: TelemetryType,
    internal: Boolean,
    private: Boolean,
    parent: EmbraceSpan? = null,
): OtelSpanBuilderWrapper {
    val spanName: String = if (internal) {
        name.toEmbraceObjectName()
    } else {
        name
    }

    // If there is a parent, extract the wrapped OTel span and set it as the parent in the wrapped OTel SpanBuilder
    val parentContext = if (parent is EmbraceSdkSpan) {
        val newParentContext = parent.asNewContext() ?: Context.root()
        newParentContext.with(parent)
    } else {
        Context.root()
    }

    val spanBuilder = spanBuilder(spanName).apply {
        if (parentContext != null) {
            setParent(parentContext)
        } else {
            setNoParent()
        }
    }

    val embraceAttributes = listOf(type) + if (private) {
        listOf(PrivateSpan)
    } else {
        emptyList()
    }

    return OtelSpanBuilderWrapper(
        spanBuilder = spanBuilder,
        parentContext = parentContext,
        initialSpanName = spanName,
        internal = internal,
        embraceAttributes = embraceAttributes
    )
}
