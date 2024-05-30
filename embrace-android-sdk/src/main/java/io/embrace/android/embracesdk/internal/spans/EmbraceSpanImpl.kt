package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.utils.truncatedStacktraceText
import io.embrace.android.embracesdk.opentelemetry.exceptionMessage
import io.embrace.android.embracesdk.opentelemetry.exceptionStacktrace
import io.embrace.android.embracesdk.opentelemetry.exceptionType
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent.Companion.inputsValid
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.sdk.common.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class EmbraceSpanImpl(
    private val spanBuilder: EmbraceSpanBuilder,
    private val openTelemetryClock: Clock,
    private val spanRepository: SpanRepository,
) : PersistableEmbraceSpan {

    private val startedSpan: AtomicReference<io.opentelemetry.api.trace.Span?> = AtomicReference(null)
    private var spanStartTimeMs: Long? = null
    private var spanEndTimeMs: Long? = null
    private var status = Span.Status.UNSET
    private var updatedName: String? = null
    private val events = ConcurrentLinkedQueue<EmbraceSpanEvent>()
    private val schemaAttributes = spanBuilder.getFixedAttributes().associate {
        it.toEmbraceKeyValuePair()
    }.toMutableMap()
    private val attributes = ConcurrentHashMap<String, String>()

    // size for ConcurrentLinkedQueues is not a constant operation, so it could be subject to race conditions
    // do the bookkeeping separately so we don't have to worry about this
    private val eventCount = AtomicInteger(0)

    override val parent: EmbraceSpan? = spanBuilder.parent

    override val spanContext: SpanContext?
        get() = startedSpan.get()?.spanContext

    override val traceId: String?
        get() = spanContext?.traceId

    override val spanId: String?
        get() = spanContext?.spanId

    override val isRecording: Boolean
        get() = startedSpan.get()?.isRecording == true

    override fun start(startTimeMs: Long?): Boolean {
        return if (spanStarted()) {
            false
        } else {
            var successful: Boolean
            val attemptedStartTimeMs = startTimeMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis()
            synchronized(startedSpan) {
                startedSpan.set(spanBuilder.startSpan(attemptedStartTimeMs))
                successful = spanStarted()
            }
            if (successful) {
                updatedName?.let { newName ->
                    startedSpan.get()?.updateName(newName)
                }

                spanStartTimeMs = attemptedStartTimeMs
                spanRepository.trackStartedSpan(this)
            }
            return successful
        }
    }

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        return if (!isRecording) {
            false
        } else {
            var successful = false
            val attemptedEndTimeMs = endTimeMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis()

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
                spanId?.let { spanRepository.trackedSpanStopped(it) }
            }
            return successful
        }
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        recordEvent(name = name, attributes = attributes) {
            EmbraceSpanEvent.create(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis(),
                attributes = attributes
            )
        }

    override fun recordException(exception: Throwable, attributes: Map<String, String>?): Boolean =
        recordEvent(name = EXCEPTION_EVENT_NAME, attributes = attributes) {
            val eventAttributes = mutableMapOf<String, String>()
            if (attributes != null) {
                eventAttributes.putAll(attributes)
            }

            exception.javaClass.canonicalName?.let { type ->
                eventAttributes[exceptionType.key] = type
            }

            exception.message?.let { message ->
                eventAttributes[exceptionMessage.key] = message
            }

            eventAttributes[exceptionStacktrace.key] = exception.truncatedStacktraceText()

            EmbraceSpanEvent.create(
                name = EXCEPTION_EVENT_NAME,
                timestampMs = openTelemetryClock.now().nanosToMillis(),
                attributes = eventAttributes
            )
        }

    override fun removeEvents(type: EmbType): Boolean {
        synchronized(eventCount) {
            events.forEach { event ->
                if (event.hasFixedAttribute(type)) {
                    events.remove(event)
                    eventCount.decrementAndGet()
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

    override fun updateName(newName: String): Boolean {
        if (newName.isValidName()) {
            synchronized(startedSpan) {
                if (!spanStarted() || isRecording) {
                    updatedName = newName
                    startedSpan.get()?.updateName(newName)
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
                name = getSpanName(),
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

    override fun hasEmbraceAttribute(fixedAttribute: FixedAttribute): Boolean =
        allAttributes().hasFixedAttribute(fixedAttribute)

    override fun getAttribute(key: EmbraceAttributeKey): String? = allAttributes()[key.name]

    override fun removeCustomAttribute(key: String): Boolean = attributes.remove(key) != null

    private fun allAttributes(): Map<String, String> = attributes + schemaAttributes

    private fun canSnapshot(): Boolean = spanId != null && spanStartTimeMs != null

    private fun recordEvent(name: String, attributes: Map<String, String>?, eventSupplier: () -> EmbraceSpanEvent?): Boolean {
        if (eventCount.get() < MAX_EVENT_COUNT && inputsValid(name, attributes)) {
            synchronized(eventCount) {
                if (eventCount.get() < MAX_EVENT_COUNT && isRecording) {
                    eventSupplier()?.apply {
                        events.add(this)
                        eventCount.incrementAndGet()
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun spanStarted() = startedSpan.get() != null

    private fun getSpanName() = synchronized(startedSpan) { updatedName ?: spanBuilder.spanName }

    internal fun wrappedSpan(): io.opentelemetry.api.trace.Span? = startedSpan.get()

    companion object {
        internal const val MAX_NAME_LENGTH = 50
        internal const val MAX_EVENT_COUNT = 10
        internal const val MAX_ATTRIBUTE_COUNT = 50
        internal const val MAX_ATTRIBUTE_KEY_LENGTH = 50
        internal const val MAX_ATTRIBUTE_VALUE_LENGTH = 500
        internal const val EXCEPTION_EVENT_NAME = "exception"

        internal fun attributeValid(key: String, value: String) =
            key.length <= MAX_ATTRIBUTE_KEY_LENGTH && value.length <= MAX_ATTRIBUTE_VALUE_LENGTH

        internal fun EmbraceSpan.setFixedAttribute(fixedAttribute: FixedAttribute): EmbraceSpan {
            addAttribute(fixedAttribute.key.name, fixedAttribute.value)
            return this
        }

        internal fun String.isValidName(): Boolean = isNotBlank() && (length <= MAX_NAME_LENGTH)
    }
}
