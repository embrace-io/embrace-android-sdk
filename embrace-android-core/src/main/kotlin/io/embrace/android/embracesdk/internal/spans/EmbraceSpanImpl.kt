package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute.Failure.fromErrorCode
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanLimits.EXCEPTION_EVENT_NAME
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanLimits.MAX_CUSTOM_ATTRIBUTE_COUNT
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanLimits.MAX_CUSTOM_EVENT_COUNT
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanLimits.MAX_TOTAL_EVENT_COUNT
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanLimits.isAttributeValid
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanLimits.isNameValid
import io.embrace.android.embracesdk.internal.utils.truncatedStacktraceText
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.semconv.ExceptionAttributes
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class EmbraceSpanImpl(
    private val spanBuilder: EmbraceSpanBuilder,
    private val openTelemetryClock: Clock,
    private val spanRepository: SpanRepository,
    private val sensitiveKeysBehavior: SensitiveKeysBehavior?,
) : PersistableEmbraceSpan {

    private val startedSpan: AtomicReference<io.opentelemetry.api.trace.Span?> = AtomicReference(null)
    private var spanStartTimeMs: Long? = null
    private var spanEndTimeMs: Long? = null
    private var status = Span.Status.UNSET
    private var updatedName: String? = null
    private val systemEvents = ConcurrentLinkedQueue<EmbraceSpanEvent>()
    private val customEvents = ConcurrentLinkedQueue<EmbraceSpanEvent>()
    private val systemAttributes = ConcurrentHashMap<String, String>().apply {
        putAll(spanBuilder.getFixedAttributes().associate { it.key.attributeKey.key to it.value })
    }
    private val customAttributes = ConcurrentHashMap<String, String>().apply {
        putAll(spanBuilder.getCustomAttributes())
    }

    // size for ConcurrentLinkedQueues is not a constant operation, so it could be subject to race conditions
    // do the bookkeeping separately so we don't have to worry about this
    private val systemEventCount = AtomicInteger(0)
    private val customEventCount = AtomicInteger(0)

    override val parent: EmbraceSpan? = spanBuilder.getParentSpan()

    override val spanContext: SpanContext?
        get() = startedSpan.get()?.spanContext

    override val traceId: String?
        get() = spanContext?.traceId

    override val spanId: String?
        get() = spanContext?.spanId

    override val isRecording: Boolean
        get() = startedSpan.get()?.isRecording == true

    override val autoTerminationMode: AutoTerminationMode = spanBuilder.autoTerminationMode

    override fun start(startTimeMs: Long?): Boolean {
        if (spanStarted()) {
            return false
        }

        val attemptedStartTimeMs =
            (startTimeMs?.normalizeTimestampAsMillis() ?: spanBuilder.startTimeMs)?.takeIf { it > 0 }
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
            spanRepository.notifySpanUpdate()
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
                    spanToStop.setAttribute(systemAttribute.key, systemAttribute.value)
                }
                customAttributes.redactIfSensitive().forEach { attribute ->
                    spanToStop.setAttribute(attribute.key, attribute.value)
                }

                val redactedCustomEvents = customEvents.map { it.copy(attributes = it.attributes.redactIfSensitive()) }

                (systemEvents + redactedCustomEvents).forEach { event ->
                    val eventAttributes = if (event.attributes.isNotEmpty()) {
                        Attributes.builder().fromMap(event.attributes, spanBuilder.internal).build()
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
                    spanRepository.notifySpanUpdate()
                }
            }
        }

        return successful
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        recordEvent(customEvents, customEventCount, MAX_CUSTOM_EVENT_COUNT) {
            EmbraceSpanEvent.create(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis(),
                attributes = attributes
            )
        }

    override fun recordException(exception: Throwable, attributes: Map<String, String>?): Boolean =
        recordEvent(customEvents, customEventCount, MAX_CUSTOM_EVENT_COUNT) {
            val eventAttributes = mutableMapOf<String, String>()
            if (attributes != null) {
                eventAttributes.putAll(attributes)
            }

            exception.javaClass.canonicalName?.let { type ->
                eventAttributes[ExceptionAttributes.EXCEPTION_TYPE.key] = type
            }

            exception.message?.let { message ->
                eventAttributes[ExceptionAttributes.EXCEPTION_MESSAGE.key] = message
            }

            eventAttributes[ExceptionAttributes.EXCEPTION_STACKTRACE.key] = exception.truncatedStacktraceText()

            EmbraceSpanEvent.create(
                name = EXCEPTION_EVENT_NAME,
                timestampMs = openTelemetryClock.now().nanosToMillis(),
                attributes = eventAttributes
            )
        }

    override fun addSystemEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        recordEvent(systemEvents, systemEventCount, MAX_TOTAL_EVENT_COUNT) {
            EmbraceSpanEvent.create(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis(),
                attributes = attributes
            )
        }

    override fun removeSystemEvents(type: EmbType): Boolean {
        synchronized(systemEventCount) {
            systemEvents.forEach { event ->
                if (event.hasFixedAttribute(type)) {
                    systemEvents.remove(event)
                    systemEventCount.decrementAndGet()
                    spanRepository.notifySpanUpdate()
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
                spanRepository.notifySpanUpdate()
            }
        }
    }

    override fun addAttribute(key: String, value: String): Boolean {
        if (customAttributes.size < MAX_CUSTOM_ATTRIBUTE_COUNT && isAttributeValid(key, value, spanBuilder.internal)) {
            synchronized(customAttributes) {
                if (customAttributes.size < MAX_CUSTOM_ATTRIBUTE_COUNT && isRecording) {
                    customAttributes[key] = value
                    spanRepository.notifySpanUpdate()
                    return true
                }
            }
        }

        return false
    }

    override fun updateName(newName: String): Boolean {
        if (newName.isNameValid(spanBuilder.internal)) {
            synchronized(startedSpan) {
                if (!spanStarted() || isRecording) {
                    updatedName = newName
                    startedSpan.get()?.updateName(newName)
                    spanRepository.notifySpanUpdate()
                    return true
                }
            }
        }

        return false
    }

    override fun asNewContext(): Context? = startedSpan.get()?.run { spanBuilder.parentContext.with(this) }

    override fun snapshot(): Span? {
        val redactedCustomEvents = customEvents.map { it.copy(attributes = it.attributes.redactIfSensitive()) }
        return if (canSnapshot()) {
            Span(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parent?.spanId ?: SpanId.getInvalid(),
                name = getSpanName(),
                startTimeNanos = spanStartTimeMs?.millisToNanos(),
                endTimeNanos = spanEndTimeMs?.millisToNanos(),
                status = status,
                events = systemEvents.map(EmbraceSpanEvent::toNewPayload) + redactedCustomEvents.map(EmbraceSpanEvent::toNewPayload),
                attributes = getAttributesPayload()
            )
        } else {
            null
        }
    }

    override fun hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
        systemAttributes[fixedAttribute.key.attributeKey.key] == fixedAttribute.value

    override fun getSystemAttribute(key: AttributeKey<String>): String? = systemAttributes[key.key]

    override fun setSystemAttribute(key: AttributeKey<String>, value: String) {
        addSystemAttribute(key.key, value)
    }

    override fun addSystemAttribute(key: String, value: String) {
        systemAttributes[key] = value
        spanRepository.notifySpanUpdate()
    }

    override fun removeSystemAttribute(key: String) {
        systemAttributes.remove(key)
        spanRepository.notifySpanUpdate()
    }

    private fun getAttributesPayload(): List<Attribute> =
        systemAttributes.map { Attribute(it.key, it.value) } + customAttributes.redactIfSensitive().toNewPayload()

    private fun canSnapshot(): Boolean = spanId != null && spanStartTimeMs != null

    private fun recordEvent(
        events: Queue<EmbraceSpanEvent>,
        count: AtomicInteger,
        max: Int,
        eventSupplier: () -> EmbraceSpanEvent?,
    ): Boolean {
        if (count.get() < max) {
            synchronized(count) {
                if (count.get() < max && isRecording) {
                    eventSupplier()?.apply {
                        events.add(this)
                        count.incrementAndGet()
                        spanRepository.notifySpanUpdate()
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun spanStarted() = startedSpan.get() != null

    private fun getSpanName() = synchronized(startedSpan) { updatedName ?: spanBuilder.spanName }

    private fun Map<String, String>.redactIfSensitive(): Map<String, String> {
        return mapValues {
            if (sensitiveKeysBehavior != null && sensitiveKeysBehavior.isSensitiveKey(it.key)) {
                REDACTED_LABEL
            } else {
                it.value
            }
        }
    }
}
