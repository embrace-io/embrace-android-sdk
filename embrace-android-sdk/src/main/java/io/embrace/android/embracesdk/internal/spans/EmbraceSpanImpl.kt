package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.utils.truncatedStacktraceText
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent.Companion.inputsValid
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.semconv.incubating.ExceptionIncubatingAttributes
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
    private val systemAttributes = ConcurrentHashMap<EmbraceAttributeKey, String>().apply {
        putAll(spanBuilder.getFixedAttributes().associate { it.key to it.value })
    }
    private val customAttributes = ConcurrentHashMap<String, String>().apply {
        putAll(spanBuilder.getCustomAttributes())
    }

    // size for ConcurrentLinkedQueues is not a constant operation, so it could be subject to race conditions
    // do the bookkeeping separately so we don't have to worry about this
    private val eventCount = AtomicInteger(0)

    override val parent: EmbraceSpan? = spanBuilder.getParentSpan()

    override val spanContext: SpanContext?
        get() = startedSpan.get()?.spanContext

    override val traceId: String?
        get() = spanContext?.traceId

    override val spanId: String?
        get() = spanContext?.spanId

    override val isRecording: Boolean
        get() = startedSpan.get()?.isRecording == true

    override fun start(startTimeMs: Long?): Boolean {
        if (spanStarted()) {
            return false
        }

        val attemptedStartTimeMs = startTimeMs?.normalizeTimestampAsMillis()
            ?: spanBuilder.startTimeMs
            ?: openTelemetryClock.now().nanosToMillis()

        synchronized(startedSpan) {
            val newSpan = spanBuilder.startSpan(attemptedStartTimeMs)
            if (newSpan.isRecording) {
                startedSpan.set(newSpan)
            } else {
                return false
            }

            spanRepository.trackStartedSpan(this)
            updatedName?.let { newName ->
                newSpan.updateName(newName)
            }
            spanStartTimeMs = attemptedStartTimeMs
        }

        return true
    }

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        if (!isRecording) {
            return false
        }
        var successful = false
        val attemptedEndTimeMs = endTimeMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis()

        synchronized(startedSpan) {
            if (!isRecording) {
                return false
            }

            startedSpan.get()?.let { spanToStop ->
                systemAttributes.forEach { systemAttribute ->
                    spanToStop.setEmbraceAttribute(systemAttribute.key, systemAttribute.value)
                }
                customAttributes.forEach { attribute ->
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

                if (errorCode != null) {
                    setStatus(StatusCode.ERROR)
                    spanToStop.setFixedAttribute(errorCode.fromErrorCode())
                } else if (status == Span.Status.ERROR) {
                    spanToStop.setFixedAttribute(ErrorCodeAttribute.Failure)
                }

                spanToStop.end(attemptedEndTimeMs, TimeUnit.MILLISECONDS)
                successful = !isRecording
                if (successful) {
                    spanId?.let { spanRepository.trackedSpanStopped(it) }
                    spanEndTimeMs = attemptedEndTimeMs
                }
            }
        }

        return successful
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
                eventAttributes[ExceptionIncubatingAttributes.EXCEPTION_TYPE.key] = type
            }

            exception.message?.let { message ->
                eventAttributes[ExceptionIncubatingAttributes.EXCEPTION_MESSAGE.key] = message
            }

            eventAttributes[ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE.key] = exception.truncatedStacktraceText()

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

    override fun setStatus(statusCode: StatusCode, description: String) {
        startedSpan.get()?.let { sdkSpan ->
            synchronized(startedSpan) {
                status = statusCode.toStatus()
                sdkSpan.setStatus(statusCode, description)
            }
        }
    }

    override fun addAttribute(key: String, value: String): Boolean {
        if (customAttributes.size < MAX_ATTRIBUTE_COUNT && attributeValid(key, value)) {
            synchronized(customAttributes) {
                if (customAttributes.size < MAX_ATTRIBUTE_COUNT && isRecording) {
                    customAttributes[key] = value
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

    override fun asNewContext(): Context? = startedSpan.get()?.run { spanBuilder.parentContext.with(this) }

    override fun snapshot(): Span? {
        return if (canSnapshot()) {
            Span(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parent?.spanId ?: SpanId.getInvalid(),
                name = getSpanName(),
                startTimeNanos = spanStartTimeMs?.millisToNanos(),
                endTimeNanos = spanEndTimeMs?.millisToNanos() ?: openTelemetryClock.now(),
                status = status,
                events = events.map(EmbraceSpanEvent::toNewPayload),
                attributes = getAttributesPayload()
            )
        } else {
            null
        }
    }

    override fun hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean = systemAttributes[fixedAttribute.key] == fixedAttribute.value

    override fun getSystemAttribute(key: EmbraceAttributeKey): String? = systemAttributes[key]

    override fun setSystemAttribute(key: EmbraceAttributeKey, value: String) {
        systemAttributes[key] = value
    }

    override fun removeCustomAttribute(key: String): Boolean = customAttributes.remove(key) != null

    private fun getAttributesPayload(): List<Attribute> =
        systemAttributes.map { Attribute(it.key.name, it.value) } + customAttributes.toNewPayload()

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

    companion object {
        internal const val MAX_NAME_LENGTH = 50
        internal const val MAX_EVENT_COUNT = 10
        internal const val MAX_ATTRIBUTE_COUNT = 50
        internal const val MAX_ATTRIBUTE_KEY_LENGTH = 50
        internal const val MAX_ATTRIBUTE_VALUE_LENGTH = 500
        internal const val EXCEPTION_EVENT_NAME = "exception"

        internal fun attributeValid(key: String, value: String) =
            key.length <= MAX_ATTRIBUTE_KEY_LENGTH && value.length <= MAX_ATTRIBUTE_VALUE_LENGTH

        internal fun String.isValidName(): Boolean = isNotBlank() && (length <= MAX_NAME_LENGTH)
    }
}
