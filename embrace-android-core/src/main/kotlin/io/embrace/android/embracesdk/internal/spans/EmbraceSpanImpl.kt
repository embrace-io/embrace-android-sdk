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
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.isAttributeValid
import io.embrace.android.embracesdk.internal.config.instrumented.isNameValid
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
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
    private val limits: OtelLimitsConfig = InstrumentedConfigImpl.otelLimits,
) : PersistableEmbraceSpan {

    private companion object {
        private const val EXCEPTION_EVENT_NAME = "exception"
    }

    private val startedSpan: AtomicReference<io.opentelemetry.api.trace.Span?> = AtomicReference(null)
    private var spanStartTimeMs: Long? = null
    private var spanEndTimeMs: Long? = null
    private var status = Span.Status.UNSET
    private var updatedName: String? = null
    private val events = ConcurrentLinkedQueue<EmbraceSpanEvent>()
    private val attributes = ConcurrentHashMap<String, String>().apply {
        putAll(spanBuilder.getFixedAttributes().associate { it.key.attributeKey.key to it.value })
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
                attributes.redactIfSensitive().forEach { attribute ->
                    spanToStop.setAttribute(attribute.key, attribute.value)
                }

                val redactedEvents = events.map { it.copy(attributes = it.attributes.redactIfSensitive()) }

                redactedEvents.forEach { event ->
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
                    spanRepository.notifySpanUpdate()
                }
            }
        }

        return successful
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        recordEvent {
            EmbraceSpanEvent.create(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis(),
                attributes = attributes
            )
        }

    override fun recordException(exception: Throwable, attributes: Map<String, String>?): Boolean =
        recordEvent {
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

    override fun removeEvents(type: EmbType): Boolean {
        synchronized(eventCount) {
            events.forEach { event ->
                if (event.hasFixedAttribute(type)) {
                    events.remove(event)
                    eventCount.decrementAndGet()
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
        if (attributes.size < limits.getMaxAttributeCount() && limits.isAttributeValid(key, value)) {
            synchronized(attributes) {
                if (attributes.size < limits.getMaxAttributeCount() && isRecording) {
                    attributes[key] = value
                    spanRepository.notifySpanUpdate()
                    return true
                }
            }
        }

        return false
    }

    override fun updateName(newName: String): Boolean {
        if (limits.isNameValid(newName)) {
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
        val redactedEvents = events.map { it.copy(attributes = it.attributes.redactIfSensitive()) }
        return if (canSnapshot()) {
            Span(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parent?.spanId ?: SpanId.getInvalid(),
                name = getSpanName(),
                startTimeNanos = spanStartTimeMs?.millisToNanos(),
                endTimeNanos = spanEndTimeMs?.millisToNanos(),
                status = status,
                events = redactedEvents.map(EmbraceSpanEvent::toNewPayload),
                attributes = getAttributesPayload()
            )
        } else {
            null
        }
    }

    override fun getAttribute(key: AttributeKey<String>): String? = attributes[key.key]

    override fun hasFixedAttribute(fixedAttribute: FixedAttribute): Boolean =
        attributes[fixedAttribute.key.attributeKey.key] == fixedAttribute.value

    override fun removeAttribute(key: String) {
        attributes.remove(key)
        spanRepository.notifySpanUpdate()
    }

    private fun getAttributesPayload(): List<Attribute> = attributes.redactIfSensitive().map { Attribute(it.key, it.value) }

    private fun canSnapshot(): Boolean = spanId != null && spanStartTimeMs != null

    private fun recordEvent(eventSupplier: () -> EmbraceSpanEvent?): Boolean {
        val max = limits.getMaxEventCount()
        if (eventCount.get() < max) {
            synchronized(eventCount) {
                if (eventCount.get() < max && isRecording) {
                    eventSupplier()?.apply {
                        events.add(this)
                        eventCount.incrementAndGet()
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
