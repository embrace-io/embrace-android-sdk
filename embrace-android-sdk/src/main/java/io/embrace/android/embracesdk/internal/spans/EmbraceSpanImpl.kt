package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.EmbraceAttribute
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent.Companion.inputsValid
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class EmbraceSpanImpl(
    private val spanName: String,
    private val openTelemetryClock: Clock,
    private val spanBuilder: EmbraceSpanBuilder,
    override val parent: EmbraceSpan? = null,
    private val spanRepository: SpanRepository? = null
) : PersistableEmbraceSpan {

    private val startedSpan: AtomicReference<io.opentelemetry.api.trace.Span?> = AtomicReference(null)
    private var spanStartTimeMs: Long? = null
    private var spanEndTimeMs: Long? = null
    private var status = Span.Status.UNSET
    private val events = ConcurrentLinkedQueue<EmbraceSpanEvent>()
    private val schemaAttributes = spanBuilder.embraceAttributes.associate { it.toOTelKeyValuePair() }.toMutableMap()
    private val attributes = ConcurrentHashMap<String, String>()

    // size for ConcurrentLinkedQueues is not a constant operation, so it could be subject to race conditions
    // do the bookkeeping separately so we don't have to worry about this
    private val eventCount = AtomicInteger(0)

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
            val attemptedStartTimeMs = startTimeMs ?: openTelemetryClock.now().nanosToMillis()
            synchronized(startedSpan) {
                startedSpan.set(spanBuilder.startSpan(attemptedStartTimeMs))
                successful = startedSpan.get() != null
            }
            if (successful) {
                spanStartTimeMs = attemptedStartTimeMs
                spanRepository?.trackStartedSpan(this)
            }
            return successful
        }
    }

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        return if (!isRecording) {
            false
        } else {
            var successful = false
            val attemptedEndTimeMs = endTimeMs ?: openTelemetryClock.now().nanosToMillis()

            synchronized(startedSpan) {
                startedSpan.get()?.let { spanToStop ->
                    allAttributes().forEach { attribute ->
                        spanToStop.setAttribute(attribute.key, attribute.value)
                    }

                    events.forEach { event ->
                        val eventAttributes = if (event.attributes.isNotEmpty()) {
                            Attributes.builder().fromMap(event.attributes).build()
                        } else {
                            Attributes.empty()
                        }

                        spanToStop.addEvent(
                            event.name,
                            eventAttributes,
                            event.timestampNanos,
                            TimeUnit.NANOSECONDS
                        )
                    }
                    spanToStop.endSpan(errorCode, attemptedEndTimeMs)
                    successful = !isRecording
                }
            }
            if (successful) {
                status = if (errorCode != null) {
                    Span.Status.ERROR
                } else {
                    Span.Status.OK
                }
                spanEndTimeMs = attemptedEndTimeMs
                spanId?.let { spanRepository?.trackedSpanStopped(it) }
            }
            return successful
        }
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean {
        if (eventCount.get() < MAX_EVENT_COUNT && inputsValid(name, attributes)) {
            val newEvent = EmbraceSpanEvent.create(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis(),
                attributes = attributes
            )
            synchronized(eventCount) {
                if (eventCount.get() < MAX_EVENT_COUNT && isRecording) {
                    events.add(newEvent)
                    eventCount.incrementAndGet()
                    return true
                }
            }
        }

        return false
    }

    override fun addAttribute(key: String, value: String): Boolean {
        if (attributes.size < MAX_ATTRIBUTE_COUNT && attributeValid(key, value)) {
            synchronized(attributes) {
                if (attributes.size < MAX_ATTRIBUTE_COUNT && isRecording) {
                    attributes[key] = value
                    return true
                }
            }
        }

        return false
    }

    override fun snapshot(): Span? {
        return if (canSnapshot()) {
            Span(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parent?.spanId,
                name = spanName,
                startTimeUnixNano = spanStartTimeMs?.millisToNanos(),
                endTimeUnixNano = spanEndTimeMs?.millisToNanos(),
                status = status,
                events = events.map(EmbraceSpanEvent::toNewPayload),
                attributes = allAttributes().toNewPayload()
            )
        } else {
            null
        }
    }

    override fun hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean = allAttributes().hasEmbraceAttribute(embraceAttribute)

    private fun allAttributes(): Map<String, String> = attributes + schemaAttributes

    private fun canSnapshot(): Boolean = spanId != null && spanStartTimeMs != null

    internal fun wrappedSpan(): io.opentelemetry.api.trace.Span? = startedSpan.get()

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
