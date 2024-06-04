package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.arch.schema.KeySpan
import io.embrace.android.embracesdk.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanKind
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
    val internal: Boolean,
    private: Boolean,
    val parent: EmbraceSpan?,
) {
    val spanName = if (internal) {
        name.toEmbraceObjectName()
    } else {
        name
    }

    var startTimeMs: Long? = null
    private val sdkSpanBuilder = tracer.spanBuilder(spanName)
    private val fixedAttributes = mutableListOf<FixedAttribute>(telemetryType)
    private val customAttributes = mutableMapOf<String, String>()

    init {
        // If there is a parent, extract the wrapped OTel span and set it as the parent in the wrapped OTel SpanBuilder
        if (parent == null) {
            setNoParent()
        } else if (parent is EmbraceSpanImpl) {
            parent.wrappedSpan()?.let {
                setParent(Context.current().with(it))
            }
        }

        if (private) {
            fixedAttributes.add(PrivateSpan)
        }
    }

    fun startSpan(startTimeMs: Long): Span {
        sdkSpanBuilder.setStartTimestamp(startTimeMs, TimeUnit.MILLISECONDS)
        return sdkSpanBuilder.startSpan()
    }

    fun getFixedAttributes(): List<FixedAttribute> = fixedAttributes

    fun getCustomAttributes(): Map<String, String> = customAttributes

    fun setParent(context: Context) {
        sdkSpanBuilder.setParent(context)
        val parentSpanContext = Span.fromContext(context).spanContext
        if (fixedAttributes.contains(EmbType.Performance.Default) && parentSpanContext != Context.root()) {
            fixedAttributes.remove(KeySpan)
        }
    }

    fun setNoParent() {
        sdkSpanBuilder.setNoParent()
        if (fixedAttributes.contains(EmbType.Performance.Default)) {
            fixedAttributes.add(KeySpan)
        }
    }

    fun setSpanKind(spanKind: SpanKind) {
        sdkSpanBuilder.setSpanKind(spanKind)
    }
}
