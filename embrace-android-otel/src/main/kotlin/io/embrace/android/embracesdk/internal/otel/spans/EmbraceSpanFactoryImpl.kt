package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.asPair
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute.Failure.fromErrorCode
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.fromMap
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.sdk.otelSpanCreator
import io.embrace.android.embracesdk.internal.otel.sdk.setEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.toStringMap
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.truncatedStacktraceText
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.k2j.tracing.SpanContextAdapter
import io.embrace.opentelemetry.kotlin.k2j.tracing.convertToOtelJava
import io.embrace.opentelemetry.kotlin.tracing.StatusCode
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import io.opentelemetry.context.ImplicitContextKeyed
import io.opentelemetry.context.Scope
import io.opentelemetry.semconv.ExceptionAttributes
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalApi::class)
class EmbraceSpanFactoryImpl(
    private val tracer: Tracer,
    private val openTelemetryClock: Clock,
    private val spanRepository: SpanRepository,
    private val dataValidator: DataValidator,
    private val stopCallback: ((spanId: String) -> Unit)? = null,
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
        otelSpanCreator = tracer.otelSpanCreator(
            name = name,
            type = type,
            internal = internal,
            private = private,
            parent = parent,
        ),
        autoTerminationMode = autoTerminationMode
    )

    override fun create(
        otelSpanCreator: OtelSpanCreator,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSdkSpan {
        return EmbraceSpanImpl(
            otelSpanCreator = otelSpanCreator,
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository,
            dataValidator = dataValidator,
            stopCallback = stopCallback,
            redactionFunction = redactionFunction,
            autoTerminationMode = autoTerminationMode
        )
    }
}

@OptIn(ExperimentalApi::class)
private class EmbraceSpanImpl(
    private val otelSpanCreator: OtelSpanCreator,
    private val openTelemetryClock: Clock,
    private val spanRepository: SpanRepository,
    private val dataValidator: DataValidator,
    private val stopCallback: ((spanId: String) -> Unit)? = null,
    private val redactionFunction: ((key: String, value: String) -> String)? = null,
    override val autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
) : EmbraceSdkSpan {

    private val startedSpan: AtomicReference<Span?> = AtomicReference(null)

    @Volatile
    private var spanStartTimeMs: Long? = null

    @Volatile
    private var spanEndTimeMs: Long? = null

    @Volatile
    override var status = io.embrace.android.embracesdk.internal.payload.Span.Status.UNSET
    private var updatedName: String? = null

    private val systemEvents = ConcurrentLinkedQueue<EmbraceSpanEvent>()
    private val customEvents = ConcurrentLinkedQueue<EmbraceSpanEvent>()
    private val systemAttributes = ConcurrentHashMap<String, String>().apply {
        putAll(otelSpanCreator.spanStartArgs.embraceAttributes.associate { it.key.name to it.value })
    }
    private val customAttributes = ConcurrentHashMap<String, String>().apply {
        putAll(otelSpanCreator.spanStartArgs.customAttributes)
    }
    private val systemLinks = ConcurrentLinkedQueue<EmbraceLinkData>()
    private val customLinks = ConcurrentLinkedQueue<EmbraceLinkData>()

    // size for ConcurrentLinkedQueues is not a constant operation, so it could be subject to race conditions
    // do the bookkeeping separately so we don't have to worry about this
    private val systemEventCount = AtomicInteger(0)
    private val customEventCount = AtomicInteger(0)
    private val systemLinkCount = AtomicInteger(0)
    private val customLinkCount = AtomicInteger(0)

    override val parent: EmbraceSpan? = otelSpanCreator.spanStartArgs.parentContext.getEmbraceSpan()

    override val spanContext: OtelJavaSpanContext?
        get() = startedSpan.get()?.spanContext?.convertToOtelJava()

    override val traceId: String?
        get() = spanContext?.traceId

    override val spanId: String?
        get() = spanContext?.spanId

    override val isRecording: Boolean
        get() = startedSpan.get()?.isRecording() == true

    override fun start(startTimeMs: Long?): Boolean {
        EmbTrace.trace("span-start") {
            if (spanStarted()) {
                return false
            }

            val attemptedStartTimeMs =
                (startTimeMs?.normalizeTimestampAsMillis() ?: otelSpanCreator.spanStartArgs.startTimeMs)?.takeIf { it > 0 }
                    ?: openTelemetryClock.now().nanosToMillis()

            synchronized(startedSpan) {
                val newSpan = EmbTrace.trace("otel-span-start") {
                    otelSpanCreator.startSpan(attemptedStartTimeMs)
                }
                if (newSpan.isRecording()) {
                    startedSpan.set(newSpan)
                } else {
                    return false
                }

                spanRepository.trackStartedSpan(this)
                updatedName?.let { newName ->
                    newSpan.name = newName
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
                    spanId?.let { stopCallback?.invoke(it) }
                    populateAttributes(spanToStop)
                    populateEvents(spanToStop)
                    populateLinks(spanToStop)

                    if (errorCode != null) {
                        setStatus(StatusCode.Error(null))
                        spanToStop.setEmbraceAttribute(errorCode.fromErrorCode())
                    } else if (status == io.embrace.android.embracesdk.internal.payload.Span.Status.ERROR) {
                        spanToStop.setEmbraceAttribute(ErrorCodeAttribute.Failure)
                    }

                    EmbTrace.trace("otel-span-end") {
                        spanToStop.end(attemptedEndTimeMs.millisToNanos())
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
        addObject(customEvents, customEventCount, dataValidator.otelLimitsConfig.getMaxCustomEventCount()) {
            dataValidator.createTruncatedEvent(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis(),
                internal = otelSpanCreator.spanStartArgs.internal,
                attributes = attributes ?: emptyMap(),
            )
        }

    override fun recordException(exception: Throwable, attributes: Map<String, String>?): Boolean =
        addObject(customEvents, customEventCount, dataValidator.otelLimitsConfig.getMaxCustomEventCount()) {
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

            dataValidator.createTruncatedEvent(
                name = dataValidator.otelLimitsConfig.getExceptionEventName(),
                timestampMs = openTelemetryClock.now().nanosToMillis(),
                internal = otelSpanCreator.spanStartArgs.internal,
                attributes = eventAttributes,
            )
        }

    override fun addSystemEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        addObject(systemEvents, systemEventCount, dataValidator.otelLimitsConfig.getMaxSystemEventCount()) {
            dataValidator.createTruncatedEvent(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: openTelemetryClock.now().nanosToMillis(),
                internal = otelSpanCreator.spanStartArgs.internal,
                attributes = attributes ?: emptyMap(),
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
                status = statusCode.toEmbracePayload()
                sdkSpan.status = statusCode
                spanRepository.notifySpanUpdate()
            }
        }
    }

    override fun getStartTimeMs(): Long? = spanStartTimeMs

    override fun addAttribute(key: String, value: String): Boolean {
        if (customAttributes.size < dataValidator.otelLimitsConfig.getMaxCustomAttributeCount() && key.isNotBlank()) {
            synchronized(customAttributes) {
                if (customAttributes.size < dataValidator.otelLimitsConfig.getMaxCustomAttributeCount() && isRecording) {
                    val attribute = dataValidator.truncateAttribute(
                        key = key,
                        value = value,
                        internal = otelSpanCreator.spanStartArgs.internal
                    )
                    customAttributes[attribute.first] = attribute.second
                    spanRepository.notifySpanUpdate()
                    return true
                }
            }
        }

        return false
    }

    override fun updateName(newName: String): Boolean {
        if (newName.isNotBlank()) {
            synchronized(startedSpan) {
                if (!spanStarted() || isRecording) {
                    val validatedName = dataValidator.truncateName(newName, otelSpanCreator.spanStartArgs.internal)
                    updatedName = validatedName
                    startedSpan.get()?.name = validatedName
                    spanRepository.notifySpanUpdate()
                    return true
                }
            }
        }

        return false
    }

    override fun addSystemLink(linkedSpanContext: SpanContext, type: LinkType, attributes: Map<String, String>): Boolean =
        addObject(systemLinks, systemLinkCount, dataValidator.otelLimitsConfig.getMaxSystemLinkCount()) {
            EmbraceLinkData(linkedSpanContext, mutableMapOf(type.asPair()).apply { putAll(attributes) })
        }

    override fun addLink(linkedSpanContext: OtelJavaSpanContext, attributes: Map<String, String>?): Boolean =
        addObject(customLinks, customLinkCount, dataValidator.otelLimitsConfig.getMaxCustomLinkCount()) {
            EmbraceLinkData(SpanContextAdapter(linkedSpanContext), attributes ?: emptyMap())
        }

    override fun makeCurrent(): Scope {
        val impl = startedSpan.get() as? ImplicitContextKeyed
        return if (impl != null) {
            impl.makeCurrent()
        } else {
            super.makeCurrent()
        }
    }

    override fun asNewContext(): OtelJavaContext? = startedSpan.get()?.run {
        val parentContext: OtelJavaContext = otelSpanCreator.spanStartArgs.parentContext

        // assumes that the underlying instance of Span implements ImplicitContextKeyed. This
        // should always be true when opentelemetry-kotlin is used to create spans, but
        // we avoid exposing this fact in the public interface.
        val span = this as ImplicitContextKeyed
        return span.storeInContext(parentContext)
    }

    override fun snapshot(): io.embrace.android.embracesdk.internal.payload.Span? {
        return if (canSnapshot()) {
            io.embrace.android.embracesdk.internal.payload.Span(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parent?.spanId ?: OtelIds.invalidSpanId,
                name = name(),
                startTimeNanos = spanStartTimeMs?.millisToNanos(),
                endTimeNanos = spanEndTimeMs?.millisToNanos(),
                status = status,
                events = events(),
                attributes = getAttributesPayload(),
                links = links()
            )
        } else {
            null
        }
    }

    override fun hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
        systemAttributes[embraceAttribute.key.name] == embraceAttribute.value

    override fun getSystemAttribute(key: String): String? = systemAttributes[key]

    override fun setSystemAttribute(key: String, value: String) {
        addSystemAttribute(key, value)
    }

    override fun addSystemAttribute(key: String, value: String) {
        systemAttributes[key] = value
        spanRepository.notifySpanUpdate()
    }

    override fun removeSystemAttribute(key: String) {
        systemAttributes.remove(key)
        spanRepository.notifySpanUpdate()
    }

    override fun attributes(): Map<String, Any> {
        val raw = getAttributesPayload()
        val attrs = raw.filter { it.key != null && it.data != null }
        return attrs.associate { Pair(checkNotNull(it.key), checkNotNull(it.data)) }
    }

    override fun name(): String = synchronized(startedSpan) {
        updatedName ?: otelSpanCreator.spanStartArgs.spanName
    }

    override val spanKind: SpanKind
        get() = startedSpan.get()?.spanKind ?: SpanKind.INTERNAL

    override fun events(): List<SpanEvent> {
        val redactedCustomEvents = customEvents.map { it.copy(attributes = it.attributes.redactIfSensitive()) }
        return systemEvents.map(EmbraceSpanEvent::toEmbracePayload) +
            redactedCustomEvents.map(EmbraceSpanEvent::toEmbracePayload)
    }

    override fun links(): List<Link> {
        val redactedCustomLinks = customLinks.map { it.copy(attributes = it.attributes.redactIfSensitive()) }
        return (systemLinks + redactedCustomLinks).map(EmbraceLinkData::toEmbracePayload)
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

    private fun Map<String, String>.redactIfSensitive(): Map<String, String> {
        return mapValues {
            redactionFunction?.invoke(it.key, it.value) ?: it.value
        }
    }

    private fun populateAttributes(spanToStop: Span) {
        systemAttributes.forEach { systemAttribute ->
            spanToStop.setStringAttribute(systemAttribute.key, systemAttribute.value)
        }
        customAttributes.redactIfSensitive().forEach { attribute ->
            spanToStop.setStringAttribute(attribute.key, attribute.value)
        }
    }

    private fun populateEvents(spanToStop: Span) {
        val redactedCustomEvents = customEvents.map { it.copy(attributes = it.attributes.redactIfSensitive()) }

        (systemEvents + redactedCustomEvents).forEach { event ->
            val eventAttributes = if (event.attributes.isNotEmpty()) {
                OtelJavaAttributes.builder().fromMap(event.attributes, otelSpanCreator.spanStartArgs.internal, dataValidator).build()
            } else {
                OtelJavaAttributes.empty()
            }

            spanToStop.addEvent(
                name = event.name,
                timestamp = event.timestampNanos,
            ) {
                eventAttributes.toStringMap().forEach {
                    setStringAttribute(it.key, it.value)
                }
            }
        }
    }

    private fun populateLinks(spanToStop: Span) {
        val redactedCustomLinks = customLinks.map { it.copy(attributes = it.attributes.redactIfSensitive()) }

        (systemLinks + redactedCustomLinks).forEach {
            val linkAttributes = if (it.attributes.isNotEmpty()) {
                OtelJavaAttributes.builder().fromMap(attributes = it.attributes, false, dataValidator).build()
            } else {
                OtelJavaAttributes.empty()
            }
            spanToStop.addLink(it.spanContext) {
                linkAttributes.toStringMap().forEach { entry ->
                    setStringAttribute(entry.key, entry.value)
                }
            }
        }
    }
}
