package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.asPair
import io.embrace.android.embracesdk.internal.otel.config.isAttributeValid
import io.embrace.android.embracesdk.internal.otel.config.isNameValid
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute.Failure.fromErrorCode
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.otel.sdk.fromMap
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.sdk.otelSpanBuilderWrapper
import io.embrace.android.embracesdk.internal.otel.sdk.setEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.toStatus
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.truncatedStacktraceText
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.semconv.ExceptionAttributes
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class EmbraceSpanFactoryImpl(
    private val tracer: Tracer,
    private val openTelemetryClock: Clock,
    private val spanRepository: SpanRepository,
    private var redactionFunction: ((key: String, value: String) -> String)? = null,
) : EmbraceSpanFactory {

    override fun create(
        name: String,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        parent: EmbraceSpan?,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSdkSpan = create(
        otelSpanBuilderWrapper = tracer.otelSpanBuilderWrapper(
            name = name,
            type = type,
            internal = internal,
            private = private,
            parent = parent,
        ),
        autoTerminationMode = autoTerminationMode
    )

    override fun create(
        otelSpanBuilderWrapper: OtelSpanBuilderWrapper,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSdkSpan =
        EmbraceSpanImpl(
            otelSpanBuilderWrapper = otelSpanBuilderWrapper,
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository,
            redactionFunction = redactionFunction,
            autoTerminationMode = autoTerminationMode
        )
}

private class EmbraceSpanImpl(
    private val otelSpanBuilderWrapper: OtelSpanBuilderWrapper,
    private val openTelemetryClock: Clock,
    private val spanRepository: SpanRepository,
    private val redactionFunction: ((key: String, value: String) -> String)? = null,
    private val limits: OtelLimitsConfig = InstrumentedConfigImpl.otelLimits,
    override val autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
) : EmbraceSdkSpan {

    private val startedSpan: AtomicReference<Span?> = AtomicReference(null)

    @Volatile
    private var spanStartTimeMs: Long? = null

    @Volatile
    private var spanEndTimeMs: Long? = null

    @Volatile
    private var status = io.embrace.android.embracesdk.internal.payload.Span.Status.UNSET
    private var updatedName: String? = null

    private val systemEvents = ConcurrentLinkedQueue<EmbraceSpanEvent>()
    private val customEvents = ConcurrentLinkedQueue<EmbraceSpanEvent>()
    private val systemAttributes = ConcurrentHashMap<String, String>().apply {
        putAll(otelSpanBuilderWrapper.embraceAttributes.associate { it.key.name to it.value })
    }
    private val customAttributes = ConcurrentHashMap<String, String>().apply {
        putAll(otelSpanBuilderWrapper.customAttributes)
    }
    private val systemLinks = ConcurrentLinkedQueue<EmbraceLinkData>()
    private val customLinks = ConcurrentLinkedQueue<EmbraceLinkData>()

    // size for ConcurrentLinkedQueues is not a constant operation, so it could be subject to race conditions
    // do the bookkeeping separately so we don't have to worry about this
    private val systemEventCount = AtomicInteger(0)
    private val customEventCount = AtomicInteger(0)
    private val systemLinkCount = AtomicInteger(0)
    private val customLinkCount = AtomicInteger(0)

    override val parent: EmbraceSpan? = otelSpanBuilderWrapper.getParentContext().getEmbraceSpan()

    override val spanContext: SpanContext?
        get() = startedSpan.get()?.spanContext

    override val traceId: String?
        get() = spanContext?.traceId

    override val spanId: String?
        get() = spanContext?.spanId

    override val isRecording: Boolean
        get() = startedSpan.get()?.isRecording == true

    override fun start(startTimeMs: Long?): Boolean {
        EmbTrace.trace("span-start") {
            if (spanStarted()) {
                return false
            }

            val attemptedStartTimeMs =
                (startTimeMs?.normalizeTimestampAsMillis() ?: otelSpanBuilderWrapper.startTimeMs)?.takeIf { it > 0 }
                    ?: openTelemetryClock.now().nanosToMillis()

            synchronized(startedSpan) {
                val newSpan = EmbTrace.trace("otel-span-start") {
                    otelSpanBuilderWrapper.startSpan(attemptedStartTimeMs)
                }
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
    }

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        EmbTrace.trace("span-stop") {
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
                    populateAttributes(spanToStop)
                    populateEvents(spanToStop)
                    populateLinks(spanToStop)

                    if (errorCode != null) {
                        setStatus(StatusCode.ERROR)
                        spanToStop.setEmbraceAttribute(errorCode.fromErrorCode())
                    } else if (status == io.embrace.android.embracesdk.internal.payload.Span.Status.ERROR) {
                        spanToStop.setEmbraceAttribute(ErrorCodeAttribute.Failure)
                    }

                    EmbTrace.trace("otel-span-end") {
                        spanToStop.end(attemptedEndTimeMs, TimeUnit.MILLISECONDS)
                    }

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
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        addObject(customEvents, customEventCount, limits.getMaxCustomEventCount()) {
            EmbraceSpanEvent.create(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis(),
                attributes = attributes
            )
        }

    override fun recordException(exception: Throwable, attributes: Map<String, String>?): Boolean =
        addObject(customEvents, customEventCount, limits.getMaxCustomEventCount()) {
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
                name = limits.getExceptionEventName(),
                timestampMs = openTelemetryClock.now().nanosToMillis(),
                attributes = eventAttributes
            )
        }

    override fun addSystemEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        addObject(systemEvents, systemEventCount, limits.getMaxSystemEventCount()) {
            EmbraceSpanEvent.create(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis(),
                attributes = attributes
            )
        }

    override fun removeSystemEvents(type: EmbType): Boolean {
        synchronized(systemEventCount) {
            systemEvents.forEach { event ->
                if (event.hasEmbraceAttribute(type)) {
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

    override fun getStartTimeMs(): Long? = spanStartTimeMs

    override fun addAttribute(key: String, value: String): Boolean {
        if (customAttributes.size < limits.getMaxCustomAttributeCount() &&
            limits.isAttributeValid(key, value, otelSpanBuilderWrapper.internal)
        ) {
            synchronized(customAttributes) {
                if (customAttributes.size < limits.getMaxCustomAttributeCount() && isRecording) {
                    customAttributes[key] = value
                    spanRepository.notifySpanUpdate()
                    return true
                }
            }
        }

        return false
    }

    override fun updateName(newName: String): Boolean {
        if (limits.isNameValid(newName, otelSpanBuilderWrapper.internal)) {
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

    override fun addSystemLink(linkedSpanContext: SpanContext, type: LinkType, attributes: Map<String, String>): Boolean =
        addObject(systemLinks, systemLinkCount, limits.getMaxSystemLinkCount()) {
            EmbraceLinkData(linkedSpanContext, mutableMapOf(type.asPair()).apply { putAll(attributes) })
        }

    override fun addLink(linkedSpanContext: SpanContext, attributes: Map<String, String>?): Boolean =
        addObject(customLinks, customLinkCount, limits.getMaxCustomLinkCount()) {
            EmbraceLinkData(linkedSpanContext, attributes ?: emptyMap())
        }

    override fun asNewContext(): Context? = startedSpan.get()?.run { otelSpanBuilderWrapper.getParentContext().with(this) }

    override fun snapshot(): io.embrace.android.embracesdk.internal.payload.Span? {
        val redactedCustomEvents = customEvents.map { it.copy(attributes = it.attributes.redactIfSensitive()) }
        val redactedCustomLinks = customLinks.map { it.copy(attributes = it.attributes.redactIfSensitive()) }
        return if (canSnapshot()) {
            io.embrace.android.embracesdk.internal.payload.Span(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parent?.spanId ?: OtelIds.invalidSpanId,
                name = getSpanName(),
                startTimeNanos = spanStartTimeMs?.millisToNanos(),
                endTimeNanos = spanEndTimeMs?.millisToNanos(),
                status = status,
                events = systemEvents.map(EmbraceSpanEvent::toEmbracePayload) +
                    redactedCustomEvents.map(EmbraceSpanEvent::toEmbracePayload),
                attributes = getAttributesPayload(),
                links = (systemLinks + redactedCustomLinks).map(EmbraceLinkData::toEmbracePayload)
            )
        } else {
            null
        }
    }

    override fun hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
        systemAttributes[embraceAttribute.key.name] == embraceAttribute.value

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
        systemAttributes.map { Attribute(it.key, it.value) } + customAttributes.redactIfSensitive().toEmbracePayload()

    private fun canSnapshot(): Boolean = spanId != null && spanStartTimeMs != null

    private fun <T> addObject(
        queue: Queue<T>,
        count: AtomicInteger,
        max: Int,
        objectSupplier: () -> T?,
    ): Boolean {
        if (count.get() < max) {
            synchronized(count) {
                if (count.get() < max && isRecording) {
                    objectSupplier()?.apply {
                        queue.add(this)
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

    private fun getSpanName() = synchronized(startedSpan) { updatedName ?: otelSpanBuilderWrapper.initialSpanName }

    private fun Map<String, String>.redactIfSensitive(): Map<String, String> {
        return mapValues {
            redactionFunction?.invoke(it.key, it.value) ?: it.value
        }
    }

    private fun populateAttributes(spanToStop: Span) {
        systemAttributes.forEach { systemAttribute ->
            spanToStop.setAttribute(systemAttribute.key, systemAttribute.value)
        }
        customAttributes.redactIfSensitive().forEach { attribute ->
            spanToStop.setAttribute(attribute.key, attribute.value)
        }
    }

    private fun populateEvents(spanToStop: Span) {
        val redactedCustomEvents = customEvents.map { it.copy(attributes = it.attributes.redactIfSensitive()) }

        (systemEvents + redactedCustomEvents).forEach { event ->
            val eventAttributes = if (event.attributes.isNotEmpty()) {
                Attributes.builder().fromMap(event.attributes, otelSpanBuilderWrapper.internal).build()
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
    }

    private fun populateLinks(spanToStop: Span) {
        val redactedCustomLinks = customLinks.map { it.copy(attributes = it.attributes.redactIfSensitive()) }

        (systemLinks + redactedCustomLinks).forEach {
            val linkAttributes = if (it.attributes.isNotEmpty()) {
                Attributes.builder().fromMap(attributes = it.attributes, false).build()
            } else {
                Attributes.empty()
            }
            spanToStop.addLink(it.spanContext, linkAttributes)
        }
    }
}
