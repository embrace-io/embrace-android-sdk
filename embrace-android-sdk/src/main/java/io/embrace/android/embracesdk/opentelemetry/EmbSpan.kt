package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.internal.spans.toStringMap
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class EmbSpan(
    private val embraceSpan: EmbraceSpan,
    private val clock: Clock
) : Span {

    private val pendingStatus: AtomicReference<StatusCode> = AtomicReference(StatusCode.UNSET)
    private var pendingStatusDescription: String? = null

    override fun <T : Any> setAttribute(key: AttributeKey<T>, value: T): Span {
        embraceSpan.addAttribute(key = key.key, value = value.toString())
        return this
    }

    override fun addEvent(name: String, attributes: Attributes): Span = addEvent(
        name = name,
        attributes = attributes,
        timestamp = clock.now(),
        unit = TimeUnit.NANOSECONDS
    )

    override fun addEvent(name: String, attributes: Attributes, timestamp: Long, unit: TimeUnit): Span {
        embraceSpan.addEvent(
            name = name,
            timestampMs = unit.toMillis(timestamp),
            attributes = attributes.toStringMap()
        )
        return this
    }

    override fun setStatus(statusCode: StatusCode, description: String): Span {
        if (isRecording) {
            synchronized(pendingStatus) {
                if (isRecording) {
                    pendingStatus.set(statusCode)
                    pendingStatusDescription = description
                }
            }
        }
        return this
    }

    override fun recordException(exception: Throwable, additionalAttributes: Attributes): Span {
        embraceSpan.recordException(exception, additionalAttributes.toStringMap())
        return this
    }

    override fun updateName(name: String): Span {
        embraceSpan.updateName(name)
        return this
    }

    override fun end() = end(timestamp = clock.now(), unit = TimeUnit.NANOSECONDS)

    /**
     * One difference between the implementation of this method and the equivalent implementation in the Java SDK is that [StatusCode] for
     * the underlying span is set to [StatusCode.OK] automatically if this is called before [setStatus] is called.
     */
    override fun end(timestamp: Long, unit: TimeUnit) {
        if (isRecording) {
            val endTimeMs = unit.toMillis(timestamp)
            synchronized(pendingStatus) {
                when (pendingStatus.get()) {
                    StatusCode.ERROR -> {
                        embraceSpan.stop(errorCode = ErrorCode.FAILURE, endTimeMs = endTimeMs)
                    }

                    else -> {
                        embraceSpan.stop(endTimeMs = endTimeMs)
                    }
                }
            }
        }
    }

    override fun getSpanContext(): SpanContext = embraceSpan.spanContext ?: SpanContext.getInvalid()

    override fun isRecording(): Boolean = embraceSpan.isRecording
}
