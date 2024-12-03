package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.spans.AutoTerminationMode
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
class EmbraceSpanBuilder(
    tracer: Tracer,
    name: String,
    telemetryType: TelemetryType,
    val internal: Boolean,
    private: Boolean,
    val autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    parentSpan: EmbraceSpan?,
) {
    lateinit var parentContext: Context
        private set

    val spanName: String = if (internal) {
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
        if (parentSpan is PersistableEmbraceSpan) {
            val newParentContext = parentSpan.asNewContext() ?: Context.root()
            setParentContext(newParentContext.with(parentSpan))
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

    fun getParentSpan(): EmbraceSpan? = parentContext.getEmbraceSpan()

    fun setParentContext(context: Context) {
        parentContext = context
        sdkSpanBuilder.setParent(parentContext)
    }

    fun setNoParent() {
        parentContext = Context.root()
        sdkSpanBuilder.setNoParent()
    }

    fun setSpanKind(spanKind: SpanKind) {
        sdkSpanBuilder.setSpanKind(spanKind)
    }
}
