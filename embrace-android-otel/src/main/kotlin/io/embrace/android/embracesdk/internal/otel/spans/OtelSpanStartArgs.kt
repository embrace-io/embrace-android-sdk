package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceObjectName
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.context.Context

/**
 * Wrapper for the SpanBuilder that stores the input data so that they can be accessed
 */
class OtelSpanStartArgs(
    name: String,
    type: EmbType,
    val internal: Boolean,
    private: Boolean,
    val autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    parentSpan: EmbraceSpan? = null,
) {
    var parentContext: Context = Context.root()

    val spanName: String = if (internal) {
        name.toEmbraceObjectName()
    } else {
        name
    }

    var startTimeMs: Long? = null
    var spanKind: SpanKind? = null

    val embraceAttributes = mutableListOf<EmbraceAttribute>(type)
    val customAttributes = mutableMapOf<String, String>()

    init {
        // If there is a parent, extract the wrapped OTel span and set it as the parent in the wrapped OTel SpanBuilder
        if (parentSpan is EmbraceSdkSpan) {
            val newParentContext = parentSpan.asNewContext() ?: Context.root()
            parentContext = newParentContext.with(parentSpan)
        } else {
            parentContext = Context.root()
        }

        if (private) {
            embraceAttributes.add(PrivateSpan)
        }
    }

    fun getParentSpan(): EmbraceSpan? = parentContext.getEmbraceSpan()
}
