package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.sdk.setEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.utils.truncatedStacktraceText
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.attributes.setAttributes
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class EmbraceSpanFactoryImpl(
    openTelemetryClock: Clock,
    spanRepository: SpanRepository,
    dataValidator: DataValidator,
    stopCallback: ((spanId: String) -> Unit)? = null,
    redactionFunction: ((key: String, value: String) -> String)? = null,
    telemetryService: TelemetryService,
) : EmbraceSpanFactory {

    private val deps = SpanDependencies(
        openTelemetryClock = openTelemetryClock,
        spanRepository = spanRepository,
        dataValidator = dataValidator,
        stopCallback = stopCallback,
        redactionFunction = redactionFunction,
        telemetryService = telemetryService,
    )

    override fun create(otelSpanStartArgs: OtelSpanStartArgs): EmbraceSdkSpan = EmbraceSpanImpl(
        otelSpanStartArgs = otelSpanStartArgs,
        deps = deps,
    )
}

/**
 * Optimization so that each span only carries a single reference in memory rather than one for
 * each field
 */
private class SpanDependencies(
    val openTelemetryClock: Clock,
    val spanRepository: SpanRepository,
    val dataValidator: DataValidator,
    val stopCallback: ((spanId: String) -> Unit)?,
    val redactionFunction: ((key: String, value: String) -> String)?,
    val telemetryService: TelemetryService,
)

private const val SPAN_LINK_TELEMETRY_TYPE = "span_link"
private const val SPAN_EVENT_TELEMETRY_TYPE = "span_event"

/**
 * Sentinel for a start/end time that has not been recorded.
 */
private const val UNSET_TIME = 0L

// StatusData is immutable, so the description-less error status can be shared rather than allocated per span
private val ERROR_STATUS = StatusData.Error(null)

private class EmbraceSpanImpl(
    otelSpanStartArgs: OtelSpanStartArgs,
    private val deps: SpanDependencies,
) : EmbraceSdkSpan {
    private val internal: Boolean = otelSpanStartArgs.internal

    // Retained only until the span starts, then released to avoid duplicating data already
    // copied into this span's own fields (attributes, name, etc.) and to drop the retained
    // tracer/openTelemetry references.
    private var startArgs: OtelSpanStartArgs? = otelSpanStartArgs

    private val startedSpan: AtomicReference<Span?> = AtomicReference(null)

    // stored as primitives rather than nullable Longs to avoid boxing
    @Volatile
    private var spanStartTimeMs: Long = otelSpanStartArgs.startTimeMs ?: UNSET_TIME

    @Volatile
    private var spanEndTimeMs: Long = UNSET_TIME

    override val terminationMode: SpanTerminationMode = otelSpanStartArgs.terminationMode

    override val autoTerminationMode: AutoTerminationMode get() = terminationMode.toAutoTerminationMode()

    private var spanName: String = validateName(otelSpanStartArgs.initialSpanName)
        set(name) {
            field = validateName(name)
        }

    @Volatile
    private var currentStatus: StatusData = StatusData.Unset

    override var status: StatusData
        get() = currentStatus
        set(value) {
            startedSpan.get()?.let { sdkSpan ->
                synchronized(startedSpan) {
                    sdkSpan.setStatus(value)
                    currentStatus = value
                    deps.spanRepository.notifySpanUpdate()
                }
            }
        }

    // Retained only until the span stops, then nulled out to release the queues. Nulling rather than
    // clearing keeps the release constant time, as clear() is O(n) on a ConcurrentLinkedQueue.
    @Volatile
    private var systemEvents: Queue<EmbraceSpanEvent>? = ConcurrentLinkedQueue()

    @Volatile
    private var customEvents: Queue<EmbraceSpanEvent>? = ConcurrentLinkedQueue()

    @Volatile
    private var systemLinks: Queue<EmbraceLinkData>? = ConcurrentLinkedQueue()

    @Volatile
    private var customLinks: Queue<EmbraceLinkData>? = ConcurrentLinkedQueue()

    private val systemAttributes = ConcurrentHashMap<String, String>(otelSpanStartArgs.embraceAttributes.size).apply {
        otelSpanStartArgs.embraceAttributes.forEach { put(it.key, it.value) }
    }
    private val customAttributes = ConcurrentHashMap<String, String>()

    // size for ConcurrentLinkedQueues is not a constant operation, so it could be subject to race conditions
    // do the bookkeeping separately so we don't have to worry about this
    private val systemEventCount = AtomicInteger(0)
    private val customEventCount = AtomicInteger(0)
    private val systemLinkCount = AtomicInteger(0)
    private val customLinkCount = AtomicInteger(0)

    private val parentContext = otelSpanStartArgs.parentContext

    override val parent: EmbraceSpan? = parentContext.getEmbraceSpan(otelSpanStartArgs.openTelemetry)

    override val spanContext: SpanContext?
        get() = startedSpan.get()?.spanContext

    override val traceId: String?
        get() = spanContext?.traceId

    override val spanId: String?
        get() = spanContext?.spanId

    override val isRecording: Boolean
        get() = startedSpan.get()?.isRecording() == true

    override fun start(startTimeMs: Long?): Boolean {
        if (spanStarted()) {
            return false
        }

        val requestedStartTimeMs = startTimeMs?.normalizeTimestampAsMillis() ?: spanStartTimeMs
        val attemptedStartTimeMs = if (requestedStartTimeMs > UNSET_TIME) {
            requestedStartTimeMs
        } else {
            deps.openTelemetryClock.now().nanosToMillis()
        }

        synchronized(startedSpan) {
            val args = startArgs ?: return false
            val newSpan = args.startSpan(attemptedStartTimeMs)
            if (newSpan.isRecording()) {
                startedSpan.set(newSpan)
                startArgs = null
            } else {
                return false
            }

            deps.spanRepository.trackStartedEmbraceSpan(this)
            newSpan.setName(spanName)

            spanStartTimeMs = attemptedStartTimeMs
            deps.spanRepository.notifySpanUpdate()
        }

        return true
    }

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean =
        stopWithErrorCode(errorCode?.toErrorCodeAttribute(), endTimeMs)

    override fun stopWithErrorCode(errorCode: ErrorCodeAttribute?, endTimeMs: Long?): Boolean {
        if (!isRecording) {
            return false
        }
        var successful = false
        val attemptedEndTimeMs = endTimeMs?.normalizeTimestampAsMillis() ?: deps.openTelemetryClock.now().nanosToMillis()

        synchronized(startedSpan) {
            if (!isRecording) {
                return false
            }

            startedSpan.get()?.let { spanToStop ->
                spanId?.let { deps.stopCallback?.invoke(it) }
                if (errorCode != null) {
                    status = ERROR_STATUS
                    spanToStop.setEmbraceAttribute(errorCode)
                } else if (status is StatusData.Error) {
                    spanToStop.setEmbraceAttribute(ErrorCodeAttribute.Failure)
                }

                populateAttributes(spanToStop)
                populateEvents(spanToStop)
                populateLinks(spanToStop)

                spanToStop.end(attemptedEndTimeMs.millisToNanos())

                successful = !isRecording
                if (successful) {
                    spanEndTimeMs = attemptedEndTimeMs
                    deps.spanRepository.notifySpanUpdate()
                    releaseRetainedData()
                }
            }
        }

        return successful
    }

    /**
     * Once a span has stopped it has been exported to an independent [Span] payload, so its own
     * event/link collections are redundant. Drop the references to allow GC on the collections during
     * the remainder of the session part.
     */
    private fun releaseRetainedData() {
        systemEvents = null
        customEvents = null
        systemLinks = null
        customLinks = null
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>): Boolean =
        addObject(
            queue = customEvents,
            count = customEventCount,
            max = deps.dataValidator.otelLimitsConfig.getMaxCustomEventCount(),
            telemetryType = SPAN_EVENT_TELEMETRY_TYPE,
        ) {
            deps.dataValidator.createTruncatedSpanEvent(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: deps.openTelemetryClock.now().nanosToMillis(),
                internal = internal,
                attributes = attributes,
            )
        }

    override fun recordException(exception: Throwable, attributes: Map<String, String>): Boolean =
        addObject(
            queue = customEvents,
            count = customEventCount,
            max = deps.dataValidator.otelLimitsConfig.getMaxCustomEventCount(),
            telemetryType = SPAN_EVENT_TELEMETRY_TYPE,
        ) {
            val eventAttributes = mutableMapOf<String, String>()
            eventAttributes.putAll(attributes)

            exception.javaClass.canonicalName?.let { type ->
                eventAttributes[ExceptionAttributes.EXCEPTION_TYPE] = type
            }

            exception.message?.let { message ->
                eventAttributes[ExceptionAttributes.EXCEPTION_MESSAGE] = message
            }

            eventAttributes[ExceptionAttributes.EXCEPTION_STACKTRACE] = exception.truncatedStacktraceText()

            deps.dataValidator.createTruncatedSpanEvent(
                name = deps.dataValidator.otelLimitsConfig.getExceptionEventName(),
                timestampMs = deps.openTelemetryClock.now().nanosToMillis(),
                internal = internal,
                attributes = eventAttributes,
            )
        }

    override fun addSystemEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        addObject(
            queue = systemEvents,
            count = systemEventCount,
            max = deps.dataValidator.otelLimitsConfig.getMaxSystemEventCount(),
            telemetryType = SPAN_EVENT_TELEMETRY_TYPE,
        ) {
            deps.dataValidator.createTruncatedSpanEvent(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: deps.openTelemetryClock.now().nanosToMillis(),
                internal = internal,
                attributes = attributes ?: emptyMap(),
            )
        }

    override fun getStartTimeMs(): Long? = spanStartTimeMs.takeIf { it > UNSET_TIME }

    override fun addAttribute(key: String, value: String): Boolean {
        val maxAttributeCount = deps.dataValidator.otelLimitsConfig.getMaxCustomAttributeCount()
        if (customAttributes.size < maxAttributeCount && key.isNotBlank()) {
            synchronized(customAttributes) {
                if (customAttributes.size < maxAttributeCount && isRecording) {
                    val attribute = deps.dataValidator.truncateAttribute(
                        key = key,
                        value = value,
                        internal = internal,
                    )
                    customAttributes[attribute.first] = attribute.second
                    deps.spanRepository.notifySpanUpdate()
                    return true
                }
            }
        }

        deps.telemetryService.trackAppliedLimit("span_attribute", AppliedLimitType.DROP)
        return false
    }

    override fun updateName(newName: String): Boolean {
        if (newName.isNotBlank()) {
            synchronized(startedSpan) {
                if (!spanStarted() || isRecording) {
                    spanName = newName
                    startedSpan.get()?.setName(spanName)
                    deps.spanRepository.notifySpanUpdate()
                    return true
                }
            }
        }

        return false
    }

    override fun addSystemLink(linkedSpanContext: SpanContext, type: LinkType, attributes: Map<String, String>): Boolean =
        addObject(systemLinks, systemLinkCount, deps.dataValidator.otelLimitsConfig.getMaxSystemLinkCount(), SPAN_LINK_TELEMETRY_TYPE) {
            // built in place to avoid the vararg array and Pair that mutableMapOf(type.key to type.value) allocates
            val attrs = buildMap(attributes.size + 1) {
                put(type.key, type.value)
                putAll(attributes)
            }
            EmbraceLinkData(linkedSpanContext, attrs)
        }

    override fun addLink(linkedSpanContext: SpanContext, attributes: Map<String, String>): Boolean =
        addObject(customLinks, customLinkCount, deps.dataValidator.otelLimitsConfig.getMaxCustomLinkCount(), SPAN_LINK_TELEMETRY_TYPE) {
            EmbraceLinkData(linkedSpanContext, attributes)
        }

    override fun asNewContext(): Context? = startedSpan.get()?.run {
        return parentContext.storeSpan(this)
    }

    override fun asW3cTraceParent(): String? = startedSpan.get()?.spanContext?.run { "00-$traceId-$spanId-01" }

    override fun snapshot(): io.embrace.android.embracesdk.internal.payload.Span? {
        return if (canSnapshot()) {
            io.embrace.android.embracesdk.internal.payload.Span(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parent?.spanId ?: OtelIds.INVALID_SPAN_ID,
                name = name(),
                startTimeNanos = spanStartTimeMs.takeIf { it > UNSET_TIME }?.millisToNanos(),
                endTimeNanos = spanEndTimeMs.takeIf { it > UNSET_TIME }?.millisToNanos(),
                status = status.toEmbracePayload(),
                events = events(),
                attributes = getAttributesPayload(),
                links = links(),
            )
        } else {
            null
        }
    }

    override fun hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
        systemAttributes[embraceAttribute.key] == embraceAttribute.value

    override fun getSystemAttribute(key: String): String? = systemAttributes[key]

    override fun setSystemAttribute(key: String, value: String) {
        addSystemAttribute(key, value)
    }

    override fun addSystemAttribute(key: String, value: String) {
        systemAttributes[key] = value
        deps.spanRepository.notifySpanUpdate()
    }

    override fun removeSystemAttribute(key: String) {
        systemAttributes.remove(key)
        deps.spanRepository.notifySpanUpdate()
    }

    override fun attributes(): Map<String, Any> {
        val raw = getAttributesPayload()
        val attrs = raw.filter { it.key != null && it.data != null }
        return attrs.associate { Pair(checkNotNull(it.key), checkNotNull(it.data)) }
    }

    override fun name(): String = synchronized(startedSpan) {
        spanName
    }

    override val spanKind: SpanKind = otelSpanStartArgs.spanKind ?: SpanKind.INTERNAL

    override fun events(): List<SpanEvent> =
        (systemEvents.orEmpty() + redactCustomEvents()).map(EmbraceSpanEvent::toEmbracePayload)

    override fun links(): List<Link> =
        (systemLinks.orEmpty() + redactCustomLinks()).map(EmbraceLinkData::toEmbracePayload)

    private fun redactCustomEvents(): List<EmbraceSpanEvent> = customEvents?.mapNotNull {
        EmbraceSpanEvent.create(
            name = it.name,
            timestampMs = it.timestampNanos.nanosToMillis(),
            attributes = it.attributes.redactIfSensitive(),
        )
    }.orEmpty()

    private fun redactCustomLinks(): List<EmbraceLinkData> =
        customLinks?.map { it.copy(attributes = it.attributes.redactIfSensitive()) }.orEmpty()

    private fun getAttributesPayload(): List<Attribute> =
        systemAttributes.map { Attribute(it.key, it.value) } + customAttributes.redactIfSensitive().toEmbracePayload()

    private fun canSnapshot(): Boolean = spanId != null && spanStartTimeMs > UNSET_TIME

    private fun <T> addObject(
        queue: Queue<T>?,
        count: AtomicInteger,
        max: Int,
        telemetryType: String,
        objectSupplier: () -> T?,
    ): Boolean {
        if (queue != null && count.get() < max) {
            synchronized(count) {
                if (count.get() < max && isRecording) {
                    objectSupplier()?.apply {
                        queue.add(this)
                        count.incrementAndGet()
                        deps.spanRepository.notifySpanUpdate()
                        return true
                    }
                }
            }
        }

        deps.telemetryService.trackAppliedLimit(telemetryType, AppliedLimitType.DROP)
        return false
    }

    private fun spanStarted() = startedSpan.get() != null

    private fun Map<String, String>.redactIfSensitive(): Map<String, String> {
        return mapValues {
            deps.redactionFunction?.invoke(it.key, it.value) ?: it.value
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
        val redactedCustomEvents = customEvents.orEmpty().mapNotNull {
            EmbraceSpanEvent.create(
                name = it.name,
                timestampMs = it.timestampNanos.nanosToMillis(),
                attributes = it.attributes.redactIfSensitive(),
            )
        }
        (systemEvents.orEmpty() + redactedCustomEvents).forEach { event ->
            val eventAttributes = deps.dataValidator.truncateAttributes(event.attributes, internal)

            spanToStop.addEvent(
                name = event.name,
                timestamp = event.timestampNanos,
            ) {
                setAttributes(eventAttributes)
            }
        }
    }

    private fun populateLinks(spanToStop: Span) {
        val redactedCustomLinks = customLinks.orEmpty().map { it.copy(attributes = it.attributes.redactIfSensitive()) }

        (systemLinks.orEmpty() + redactedCustomLinks).forEach {
            val linkAttributes = deps.dataValidator.truncateAttributes(it.attributes, false)
            spanToStop.addLink(it.spanContext) {
                setAttributes(linkAttributes)
            }
        }
    }

    private fun validateName(name: String) =
        deps.dataValidator.truncateName(
            name = name,
            internal = internal,
        )
}
