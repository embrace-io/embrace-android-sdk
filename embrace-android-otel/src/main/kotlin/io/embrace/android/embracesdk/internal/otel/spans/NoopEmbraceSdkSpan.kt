package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.tracing.data.StatusData
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind

@OptIn(ExperimentalApi::class)
object NoopEmbraceSdkSpan : EmbraceSdkSpan {

    override val spanContext: SpanContext? = null
    override val traceId: String? = null
    override val spanId: String? = null
    override val isRecording: Boolean = false
    override val parent: EmbraceSpan? = null
    override val autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE

    override fun start(startTimeMs: Long?): Boolean = false

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean = false

    override fun addEvent(
        name: String,
        timestampMs: Long?,
        attributes: Map<String, String>,
    ): Boolean = false

    override fun recordException(
        exception: Throwable,
        attributes: Map<String, String>,
    ): Boolean = false

    override fun addAttribute(key: String, value: String): Boolean = false

    override fun updateName(newName: String): Boolean = false

    override fun addLink(
        linkedSpanContext: SpanContext,
        attributes: Map<String, String>,
    ): Boolean = false

    override fun asNewContext(): Context? = null

    override fun snapshot(): Span? = null

    override fun hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean = false

    override fun getSystemAttribute(key: String): String? = null

    override fun setSystemAttribute(key: String, value: String) {
    }

    override fun addSystemAttribute(key: String, value: String) {
    }

    override fun removeSystemAttribute(key: String) {
    }

    override fun addSystemEvent(
        name: String,
        timestampMs: Long?,
        attributes: Map<String, String>?,
    ): Boolean = false

    override fun removeSystemEvents(type: EmbType): Boolean = false

    override fun getStartTimeMs(): Long? = null

    override fun addSystemLink(
        linkedSpanContext: SpanContext,
        type: LinkType,
        attributes: Map<String, String>,
    ): Boolean = false

    override fun attributes(): Map<String, Any> = emptyMap()

    override fun name(): String = ""

    override val spanKind: SpanKind = SpanKind.INTERNAL
    override var status: StatusData = StatusData.Unset

    override fun events(): List<SpanEvent> = emptyList()
    override fun links(): List<Link> = emptyList()
}
