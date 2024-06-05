package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.arch.schema.KeySpan
import io.embrace.android.embracesdk.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
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

    lateinit var parentContext: Context
        private set

    var startTimeMs: Long? = null

    private val sdkSpanBuilder = tracer.spanBuilder(spanName)
    private val fixedAttributes = mutableListOf<FixedAttribute>(telemetryType)
    private val customAttributes = mutableMapOf<String, String>()

    init {
        // If there is a parent, extract the wrapped OTel span and set it as the parent in the wrapped OTel SpanBuilder
        if (parent is PersistableEmbraceSpan) {
            setParent(parent.asNewContext() ?: Context.root())
        } else {
            setNoParent()
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

    fun setCustomAttribute(key: String, value: String) {
        customAttributes[key] = value
    }

    fun setParent(context: Context) {
        parentContext = context
        sdkSpanBuilder.setParent(context)
        updateKeySpan()
    }

    fun setNoParent() {
        parentContext = Context.root()
        sdkSpanBuilder.setNoParent()
        updateKeySpan()
    }

    fun setSpanKind(spanKind: SpanKind) {
        sdkSpanBuilder.setSpanKind(spanKind)
    }

    private fun updateKeySpan() {
        if (fixedAttributes.contains(EmbType.Performance.Default)) {
            if (Span.fromContext(parentContext) == Span.getInvalid()) {
                fixedAttributes.add(KeySpan)
            } else {
                fixedAttributes.remove(KeySpan)
            }
        }
    }
}
