package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.EmbraceAttribute
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent.Companion.inputsValid
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class EmbraceSpanImpl(
    private val spanBuilder: SpanBuilder,
    override val parent: EmbraceSpan? = null,
    private val spanRepository: SpanRepository? = null,
    sessionSpan: Boolean = false
) : EmbraceSpan {

    init {
        if (!sessionSpan) {
            spanBuilder.updateParent(parent)
        }
    }

    private val startedSpan: AtomicReference<Span?> = AtomicReference(null)
    private val eventCount = AtomicInteger(0)
    private val attributeCount = AtomicInteger(0)

    override val traceId: String?
        get() = startedSpan.get()?.spanContext?.traceId

    override val spanId: String?
        get() = startedSpan.get()?.spanContext?.spanId

    override val isRecording: Boolean
        get() = startedSpan.get()?.isRecording == true

    override fun start(startTimeMs: Long?): Boolean {
        return if (startedSpan.get() != null) {
            false
        } else {
            var successful: Boolean
            if (startTimeMs != null) {
                spanBuilder.setStartTimestamp(startTimeMs, TimeUnit.MILLISECONDS)
            }
            synchronized(startedSpan) {
                startedSpan.set(spanBuilder.startSpan())
                successful = startedSpan.get() != null
            }
            if (successful) {
                spanRepository?.trackStartedSpan(this)
            }
            return successful
        }
    }

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        return if (startedSpan.get()?.isRecording == false) {
            false
        } else {
            var successful: Boolean
            synchronized(startedSpan) {
                startedSpan.get()?.endSpan(errorCode, endTimeMs)
                successful = startedSpan.get()?.isRecording == false
            }
            if (successful) {
                spanId?.let { spanRepository?.trackedSpanStopped(it) }
            }
            return successful
        }
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean {
        if (eventCount.get() < MAX_EVENT_COUNT && inputsValid(name, attributes)) {
            synchronized(eventCount) {
                if (eventCount.get() < MAX_EVENT_COUNT) {
                    spanInProgress()?.let { span ->
                        if (timestampMs != null && !attributes.isNullOrEmpty()) {
                            span.addEvent(
                                name,
                                Attributes.builder().fromMap(attributes).build(),
                                timestampMs.normalizeTimestampAsMillis(),
                                TimeUnit.MILLISECONDS
                            )
                        } else if (timestampMs != null) {
                            span.addEvent(name, timestampMs.normalizeTimestampAsMillis(), TimeUnit.MILLISECONDS)
                        } else if (!attributes.isNullOrEmpty()) {
                            span.addEvent(name, Attributes.builder().fromMap(attributes).build())
                        } else {
                            span.addEvent(name)
                        }
                        eventCount.incrementAndGet()
                        return true
                    }
                }
            }
        }

        return false
    }

    override fun addAttribute(key: String, value: String): Boolean {
        if (attributeCount.get() < MAX_ATTRIBUTE_COUNT && attributeValid(key, value)) {
            synchronized(attributeCount) {
                if (attributeCount.get() < MAX_ATTRIBUTE_COUNT) {
                    spanInProgress()?.let {
                        it.setAttribute(key, value)
                        attributeCount.incrementAndGet()
                        return true
                    }
                }
            }
        }

        return false
    }

    internal fun wrappedSpan(): Span? = startedSpan.get()

    /**
     * Returns the underlying [Span] if it's currently recording
     */
    private fun spanInProgress(): Span? = startedSpan.get().takeIf { isRecording }

    companion object {
        internal const val MAX_NAME_LENGTH = 50
        internal const val MAX_EVENT_COUNT = 10
        internal const val MAX_ATTRIBUTE_COUNT = 50
        internal const val MAX_ATTRIBUTE_KEY_LENGTH = 50
        internal const val MAX_ATTRIBUTE_VALUE_LENGTH = 200

        internal fun inputsValid(
            name: String,
            events: List<EmbraceSpanEvent>? = null,
            attributes: Map<String, String>? = null
        ) =
            name.isNotBlank() &&
                name.length <= MAX_NAME_LENGTH &&
                (events == null || events.size <= MAX_EVENT_COUNT) &&
                (attributes == null || attributes.size <= MAX_ATTRIBUTE_COUNT)

        internal fun attributeValid(key: String, value: String) =
            key.length <= MAX_ATTRIBUTE_KEY_LENGTH && value.length <= MAX_ATTRIBUTE_VALUE_LENGTH

        internal fun EmbraceSpan.setEmbraceAttribute(embraceAttribute: EmbraceAttribute): EmbraceSpan {
            addAttribute(embraceAttribute.otelAttributeName(), embraceAttribute.attributeValue)
            return this
        }
    }
}
