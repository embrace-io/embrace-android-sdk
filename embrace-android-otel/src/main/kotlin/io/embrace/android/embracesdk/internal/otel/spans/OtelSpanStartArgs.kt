package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceObjectName
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.j2k.bridge.context.toOtelKotlin
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind

/**
 * Wrapper for the SpanBuilder that stores the input data so that they can be accessed
 */
@OptIn(ExperimentalApi::class)
class OtelSpanStartArgs(
    name: String,
    type: EmbType,
    val internal: Boolean,
    private: Boolean,
    private val tracer: Tracer,
    val autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    parentCtx: OtelJavaContext? = null,
    val startTimeMs: Long? = null,
    val spanKind: SpanKind? = null
) {
    val parentContext: OtelJavaContext = parentCtx ?: OtelJavaContext.root()
    val initialSpanName: String = name.prependEmbracePrefix(internal)

    val embraceAttributes = mutableListOf<EmbraceAttribute>(type)
    val customAttributes = mutableMapOf<String, String>()

    init {
        if (private) {
            embraceAttributes.add(PrivateSpan)
        }
    }

    fun getParentSpanContext(): OtelJavaSpanContext? {
        val parentSpanContext = OtelJavaSpan.fromContext(parentContext).spanContext

        return if (parentSpanContext.isValid) {
            parentSpanContext
        } else {
            null
        }
    }

    private fun String.prependEmbracePrefix(internal: Boolean): String {
        return if (internal) {
            toEmbraceObjectName()
        } else {
            this
        }
    }

    internal fun startSpan(startTimeMs: Long): Span {
        return tracer.createSpan(
            name = initialSpanName,
            parentContext = parentContext.toOtelKotlin(),
            spanKind = spanKind ?: SpanKind.INTERNAL,
            startTimestamp = startTimeMs.millisToNanos(),
        )
    }
}
