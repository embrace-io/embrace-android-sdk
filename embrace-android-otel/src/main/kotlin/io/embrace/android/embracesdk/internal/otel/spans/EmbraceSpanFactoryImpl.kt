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
import java.util.concurrent.ConcurrentHashMap
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

/**
 * Initial capacity for the lazily created event/link collections that aims
 * for a reasonable capacity most spans wouldn't fill.
 */
private const val INITIAL_COLLECTION_CAPACITY = 4

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
            if (setSpanStatus(value)) {
                notifyChanged()
            }
        }

    private fun setSpanStatus(value: StatusData): Boolean {
        val sdkSpan = startedSpan.get() ?: return false
        synchronized(startedSpan) {
            sdkSpan.setStatus(value)
            currentStatus = value
        }
        return true
    }

    /**
     * Guards mutation and snapshotting of the lazily created event/link collections and their counters.
     *
     * Deliberately not [startedSpan]: adding an event must not block behind a stop(), which holds
     * [startedSpan] while it copies every event and link onto the OTel span. Keeping the monitors
     * distinct also means a stop() that links into another span can never form a cycle.
     *
     * Lock order is always [startedSpan] then [collectionLock], never the reverse. Nothing invoked while
     * holding [collectionLock] may acquire [startedSpan].
     */
    private val collectionLock = Any()

    // Created on first successful add, as most spans never record an event or a link, and nulled out once
    // the span stops. Plain ArrayList rather than ConcurrentLinkedQueue: a queue node costs 16 bytes per
    // element against ~4 for an array slot, and every read and write is already serialised by
    // collectionLock.
    private var systemEvents: MutableList<EmbraceSpanEvent>? = null

    private var customEvents: MutableList<EmbraceSpanEvent>? = null

    private var systemLinks: MutableList<EmbraceLinkData>? = null

    private var customLinks: MutableList<EmbraceLinkData>? = null

    private val systemAttributes = ConcurrentHashMap<String, String>(otelSpanStartArgs.embraceAttributes.size).apply {
        otelSpanStartArgs.embraceAttributes.forEach { put(it.key, it.value) }
    }
    private val customAttributes = ConcurrentHashMap<String, String>()

    // Counted separately rather than read from the lists so the "already at the limit" fast path can skip
    // the lock. Plain volatile Ints rather than AtomicIntegers saves four objects per span: every write
    // happens under collectionLock, and the unsynchronized read is only a fast path for a limit that can
    // never decrease. Do not substitute List.size here, as that is a non-volatile field that can be
    // observed mid-grow().
    @Volatile
    private var systemEventCount: Int = 0

    @Volatile
    private var customEventCount: Int = 0

    @Volatile
    private var systemLinkCount: Int = 0

    @Volatile
    private var customLinkCount: Int = 0

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
        }

        notifyChanged()
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
                    setSpanStatus(ERROR_STATUS)
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
                    releaseRetainedData()
                }
            }
        }

        if (successful) {
            notifyChanged()
        }
        return successful
    }

    /**
     * Once a span has stopped it has been exported to an independent [Span] payload, so its own
     * event/link collections are redundant. Drop the references to allow GC on the collections during
     * the remainder of the session part.
     */
    private fun releaseRetainedData() {
        synchronized(collectionLock) {
            systemEvents = null
            customEvents = null
            systemLinks = null
            customLinks = null
        }
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>): Boolean =
        recordEvent(
            system = false,
            max = deps.dataValidator.otelLimitsConfig.getMaxCustomEventCount(),
        ) {
            deps.dataValidator.createTruncatedSpanEvent(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: deps.openTelemetryClock.now().nanosToMillis(),
                internal = internal,
                attributes = attributes,
            )
        }

    override fun recordException(exception: Throwable, attributes: Map<String, String>): Boolean =
        recordEvent(
            system = false,
            max = deps.dataValidator.otelLimitsConfig.getMaxCustomEventCount(),
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
        recordEvent(
            system = true,
            max = deps.dataValidator.otelLimitsConfig.getMaxSystemEventCount(),
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
            var added = false
            synchronized(customAttributes) {
                if (customAttributes.size < maxAttributeCount && isRecording) {
                    val attribute = deps.dataValidator.truncateAttribute(
                        key = key,
                        value = value,
                        internal = internal,
                    )
                    customAttributes[attribute.first] = attribute.second
                    added = true
                }
            }
            if (added) {
                notifyChanged()
                return true
            }
        }

        deps.telemetryService.trackAppliedLimit("span_attribute", AppliedLimitType.DROP)
        return false
    }

    override fun updateName(newName: String): Boolean {
        if (newName.isNotBlank()) {
            var updated = false
            synchronized(startedSpan) {
                if (!spanStarted() || isRecording) {
                    spanName = newName
                    startedSpan.get()?.setName(spanName)
                    updated = true
                }
            }
            if (updated) {
                notifyChanged()
                return true
            }
        }

        return false
    }

    override fun addSystemLink(linkedSpanContext: SpanContext, type: LinkType, attributes: Map<String, String>): Boolean =
        recordLink(system = true, max = deps.dataValidator.otelLimitsConfig.getMaxSystemLinkCount()) {
            // built in place to avoid the vararg array and Pair that mutableMapOf(type.key to type.value) allocates
            val attrs = buildMap(attributes.size + 1) {
                put(type.key, type.value)
                putAll(attributes)
            }
            EmbraceLinkData(linkedSpanContext, attrs)
        }

    override fun addLink(linkedSpanContext: SpanContext, attributes: Map<String, String>): Boolean =
        recordLink(system = false, max = deps.dataValidator.otelLimitsConfig.getMaxCustomLinkCount()) {
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
        val max = deps.dataValidator.otelLimitsConfig.getMaxSystemAttributeCount()
        if (systemAttributes.containsKey(key) || systemAttributes.size < max) {
            var added = false
            synchronized(systemAttributes) {
                if (systemAttributes.containsKey(key) || systemAttributes.size < max) {
                    systemAttributes[key] = value
                    added = true
                }
            }
            if (added) {
                notifyChanged()
                return
            }
        }
        deps.telemetryService.trackAppliedLimit("span_attribute", AppliedLimitType.DROP)
    }

    override fun removeSystemAttribute(key: String) {
        systemAttributes.remove(key)
        notifyChanged()
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

    override fun events(): List<SpanEvent> = withEventCopies { system, custom ->
        (system + redactCustomEvents(custom)).map(EmbraceSpanEvent::toEmbracePayload)
    }

    override fun links(): List<Link> = withLinkCopies { system, custom ->
        (system + redactCustomLinks(custom)).map(EmbraceLinkData::toEmbracePayload)
    }

    /**
     * Copies both event collections in a single [collectionLock] acquisition so the caller can map and
     * redact outside the lock, which matters because the redaction function is supplied by the host app.
     * Taking both at once also means a concurrent stop() cannot release one collection between the reads.
     */
    private inline fun <R> withEventCopies(
        block: (system: List<EmbraceSpanEvent>, custom: List<EmbraceSpanEvent>) -> R,
    ): R {
        val system: List<EmbraceSpanEvent>
        val custom: List<EmbraceSpanEvent>
        synchronized(collectionLock) {
            system = systemEvents?.toList().orEmpty()
            custom = customEvents?.toList().orEmpty()
        }
        return block(system, custom)
    }

    /**
     * Link equivalent of [withEventCopies].
     */
    private inline fun <R> withLinkCopies(
        block: (system: List<EmbraceLinkData>, custom: List<EmbraceLinkData>) -> R,
    ): R {
        val system: List<EmbraceLinkData>
        val custom: List<EmbraceLinkData>
        synchronized(collectionLock) {
            system = systemLinks?.toList().orEmpty()
            custom = customLinks?.toList().orEmpty()
        }
        return block(system, custom)
    }

    private fun redactCustomEvents(customEvents: List<EmbraceSpanEvent>): List<EmbraceSpanEvent> =
        customEvents.mapNotNull {
            EmbraceSpanEvent.create(
                name = it.name,
                timestampMs = it.timestampNanos.nanosToMillis(),
                attributes = it.attributes.redactIfSensitive(),
            )
        }

    private fun redactCustomLinks(customLinks: List<EmbraceLinkData>): List<EmbraceLinkData> =
        customLinks.map { it.copy(attributes = it.attributes.redactIfSensitive()) }

    private fun getAttributesPayload(): List<Attribute> =
        systemAttributes.map { Attribute(it.key, it.value) } + customAttributes.redactIfSensitive().toEmbracePayload()

    private fun canSnapshot(): Boolean = spanId != null && spanStartTimeMs > UNSET_TIME

    /**
     * Adds the event from [eventSupplier] to the system or custom event collection, unless the span is not
     * recording or that collection has reached [max]. Inlined so no closure is allocated on this hot path.
     */
    private inline fun recordEvent(system: Boolean, max: Int, eventSupplier: () -> EmbraceSpanEvent?): Boolean {
        var added = false
        if (isRecording && eventCount(system) < max) {
            synchronized(collectionLock) {
                if (eventCount(system) < max && isRecording) {
                    eventSupplier()?.let { event ->
                        if (system) {
                            systemEvents().add(event)
                            systemEventCount++
                        } else {
                            customEvents().add(event)
                            customEventCount++
                        }
                        added = true
                    }
                }
            }
        }

        if (added) {
            notifyChanged()
            return true
        }

        deps.telemetryService.trackAppliedLimit(SPAN_EVENT_TELEMETRY_TYPE, AppliedLimitType.DROP)
        return false
    }

    /**
     * Link equivalent of [recordEvent].
     */
    private inline fun recordLink(system: Boolean, max: Int, linkSupplier: () -> EmbraceLinkData): Boolean {
        var added = false
        if (isRecording && linkCount(system) < max) {
            synchronized(collectionLock) {
                if (linkCount(system) < max && isRecording) {
                    val link = linkSupplier()
                    if (system) {
                        systemLinks().add(link)
                        systemLinkCount++
                    } else {
                        customLinks().add(link)
                        customLinkCount++
                    }
                    added = true
                }
            }
        }

        if (added) {
            notifyChanged()
            return true
        }

        deps.telemetryService.trackAppliedLimit(SPAN_LINK_TELEMETRY_TYPE, AppliedLimitType.DROP)
        return false
    }

    private fun eventCount(system: Boolean): Int = if (system) systemEventCount else customEventCount

    private fun linkCount(system: Boolean): Int = if (system) systemLinkCount else customLinkCount

    // the get-or-create accessors below must only be called while holding collectionLock
    private fun systemEvents(): MutableList<EmbraceSpanEvent> =
        systemEvents ?: ArrayList<EmbraceSpanEvent>(INITIAL_COLLECTION_CAPACITY).also { systemEvents = it }

    private fun customEvents(): MutableList<EmbraceSpanEvent> =
        customEvents ?: ArrayList<EmbraceSpanEvent>(INITIAL_COLLECTION_CAPACITY).also { customEvents = it }

    private fun systemLinks(): MutableList<EmbraceLinkData> =
        systemLinks ?: ArrayList<EmbraceLinkData>(INITIAL_COLLECTION_CAPACITY).also { systemLinks = it }

    private fun customLinks(): MutableList<EmbraceLinkData> =
        customLinks ?: ArrayList<EmbraceLinkData>(INITIAL_COLLECTION_CAPACITY).also { customLinks = it }

    private fun spanStarted() = startedSpan.get() != null

    /**
     * Tells the repository this span's data changed. Must be called after the monitor is released.
     */
    private fun notifyChanged() {
        deps.spanRepository.notifySpanChanged(this)
    }

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
        withEventCopies { system, custom ->
            (system + redactCustomEvents(custom)).forEach { event ->
                val eventAttributes = deps.dataValidator.truncateAttributes(event.attributes, internal)

                spanToStop.addEvent(
                    name = event.name,
                    timestamp = event.timestampNanos,
                ) {
                    setAttributes(eventAttributes)
                }
            }
        }
    }

    private fun populateLinks(spanToStop: Span) {
        withLinkCopies { system, custom ->
            (system + redactCustomLinks(custom)).forEach {
                val linkAttributes = deps.dataValidator.truncateAttributes(it.attributes, false)
                spanToStop.addLink(it.spanContext) {
                    setAttributes(linkAttributes)
                }
            }
        }
    }

    private fun validateName(name: String) =
        deps.dataValidator.truncateName(
            name = name,
            internal = internal,
        )
}
