package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.otel.attrs.embSequenceId
import io.embrace.android.embracesdk.internal.otel.sdk.setEmbraceAttribute
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaReadWriteSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaReadableSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanExporter
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanProcessor
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import java.util.concurrent.atomic.AtomicLong

class EmbraceOtelJavaSpanProcessor(
    private val spanExporter: OtelJavaSpanExporter,
    private val sessionIdProvider: () -> String?,
    private val processIdentifier: String,
) : OtelJavaSpanProcessor {

    private val counter = AtomicLong(1)

    override fun onStart(parentContext: OtelJavaContext, span: OtelJavaReadWriteSpan) {
        span.setEmbraceAttribute(embSequenceId, counter.getAndIncrement().toString())
        span.setEmbraceAttribute(embProcessIdentifier, processIdentifier)
        sessionIdProvider()?.let { sessionId ->
            span.setAttribute(SessionIncubatingAttributes.SESSION_ID, sessionId)
        }
    }

    override fun onEnd(span: OtelJavaReadableSpan) {
        spanExporter.export(mutableListOf(span.toSpanData()))
    }

    override fun isStartRequired() = true

    override fun isEndRequired() = true
}
