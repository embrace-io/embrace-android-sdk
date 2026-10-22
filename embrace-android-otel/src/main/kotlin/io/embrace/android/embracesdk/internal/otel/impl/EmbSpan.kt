package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.attributes.AnyValue
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanCreationAction
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.createInvalidSpanContext

/**
 * Span returned to callers of the OTel API. SDK code is guarded so it never throws into the caller, and list values
 * are copied so later mutation by the caller can't affect the span. Exceptions thrown by caller-supplied attribute
 * lambdas propagate.
 */
class EmbSpan(
    private val impl: EmbraceSdkSpan,
    private val clock: Clock,
    private val errorHandler: ErrorHandler,
) : Span, SpanCreationAction {

    override fun setStringAttribute(key: String, value: String) = guard {
        impl.addCustomAttribute(key, value)
    }

    override fun setStringListAttribute(key: String, value: List<String>) = guard {
        impl.addCustomAttribute(key, value.toList())
    }

    override fun setLongListAttribute(key: String, value: List<Long>) = guard {
        impl.addCustomAttribute(key, value.toList())
    }

    override fun setLongAttribute(key: String, value: Long) = guard {
        impl.addCustomAttribute(key, value)
    }

    override fun setDoubleListAttribute(key: String, value: List<Double>) = guard {
        impl.addCustomAttribute(key, value.toList())
    }

    override fun setDoubleAttribute(key: String, value: Double) = guard {
        impl.addCustomAttribute(key, value)
    }

    override fun setBooleanListAttribute(key: String, value: List<Boolean>) = guard {
        impl.addCustomAttribute(key, value.toList())
    }

    override fun setBooleanAttribute(key: String, value: Boolean) = guard {
        impl.addCustomAttribute(key, value)
    }

    override fun setByteArrayAttribute(key: String, value: ByteArray) = guard {
        impl.addCustomAttribute(key, value)
    }

    override fun setAnyValueAttribute(key: String, value: AnyValue) = guard {
        impl.addCustomAttribute(key, value)
    }

    override fun end(): Unit = guard {
        end(timestamp = clock.now())
    }

    override fun end(timestamp: Long) = guard {
        if (isRecording()) {
            impl.stop(endTimeMs = timestamp.nanosToMillis())
        }
    }

    override val spanContext: SpanContext
        get() = guard(null) { impl.spanContext } ?: createInvalidSpanContext()

    override fun isRecording(): Boolean = guard(false) { impl.isRecording }

    override fun addEvent(name: String, timestamp: Long?, attributes: (AttributesMutator.() -> Unit)?) {
        val container = EmbAttributesMutator()
        attributes?.invoke(container)
        guard {
            impl.addCustomEvent(name, timestamp, container.attributes)
        }
    }

    override fun addLink(spanContext: SpanContext, attributes: (AttributesMutator.() -> Unit)?) {
        val container = EmbAttributesMutator()
        attributes?.invoke(container)
        guard {
            impl.addCustomLink(spanContext, container.attributes)
        }
    }

    val attributes: Map<String, Any>
        get() = guard(emptyMap()) { impl.attributes() }

    val name: String
        get() = guard("") { impl.name() }

    override fun setName(name: String) = guard {
        impl.updateName(name)
    }

    override val parent: SpanContext
        get() = guard(null) { impl.parent?.spanContext } ?: createInvalidSpanContext()

    val spanKind: SpanKind
        get() = guard(SpanKind.INTERNAL) { impl.spanKind }

    val startTimestamp: Long
        get() = guard(0L) { impl.getStartTimeMs() ?: 0 }

    val status: StatusData
        get() = guard(StatusData.Unset) { impl.status }

    override fun setStatus(status: StatusData) = guard {
        if (isRecording()) {
            impl.status = status
        }
    }

    val events: List<SpanEvent>
        get() = guard(emptyList()) { impl.events() }

    val links: List<Link>
        get() = guard(emptyList()) { impl.links() }

    private inline fun guard(action: () -> Unit) =
        errorHandler.guardSdkCode(SdkErrorType.SpanOperationFail, action)

    private inline fun <T> guard(default: T, action: () -> T): T =
        errorHandler.guardSdkCode(SdkErrorType.SpanOperationFail, default, action)
}
