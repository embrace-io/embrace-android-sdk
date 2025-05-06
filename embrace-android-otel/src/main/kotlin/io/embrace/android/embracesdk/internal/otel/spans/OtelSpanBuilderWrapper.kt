package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

/**
 * Wrapper for the [SpanBuilder] that stores the input data so that they can be accessed.
 *
 * This should only access OTel concepts that have to to proxied or altered for interoperability with Embrace concepts.
 */
class OtelSpanBuilderWrapper internal constructor(
    private val spanBuilder: SpanBuilder,
    private var parentContext: Context,
    val initialSpanName: String,
    val internal: Boolean,
    val embraceAttributes: List<EmbraceAttribute>,
) {
    var startTimeMs: Long? = null
    val customAttributes = mutableMapOf<String, String>()

    fun startSpan(startTimeMs: Long): Span {
        spanBuilder.setStartTimestamp(startTimeMs, TimeUnit.MILLISECONDS)
        return spanBuilder.startSpan()
    }

    fun getParentContext(): Context = parentContext

    fun setParentContext(context: Context) {
        parentContext = context
        spanBuilder.setParent(context)
    }

    fun setNoParent() {
        parentContext = Context.root()
        spanBuilder.setNoParent()
    }

    fun setSpanKind(spanKind: SpanKind) {
        spanBuilder.setSpanKind(spanKind)
    }
}
