package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.attrs.embHeartbeatTimeUnixNano
import io.embrace.android.embracesdk.internal.arch.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceLinkData
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.factory.toHexString
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import io.embrace.opentelemetry.kotlin.tracing.data.StatusData
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import java.util.concurrent.ConcurrentLinkedQueue

@OptIn(ExperimentalApi::class)
class FakeEmbraceSdkSpan(
    private val openTelemetry: OpenTelemetry = fakeOpenTelemetry(),
    var name: String = "fake-span",
    var parentContext: Context = openTelemetry.contextFactory.root(),
    val type: EmbType = EmbType.Performance.Default,
    val internal: Boolean = false,
    val private: Boolean = internal,
    override val autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    private val fakeClock: FakeClock = FakeClock(),
    private val otelSpanStartArgs: OtelSpanStartArgs? = null,
) : EmbraceSdkSpan {

    private var sdkSpan: Span? = null
    var spanStartTimeMs: Long? = null
    var spanEndTimeMs: Long? = null
    override var status: StatusData = StatusData.Unset
    var errorCode: ErrorCode? = null
    val attributes: MutableMap<String, String> = mutableMapOf(type.asPair())
    val events: ConcurrentLinkedQueue<EmbraceSpanEvent> = ConcurrentLinkedQueue()
    val links: ConcurrentLinkedQueue<EmbraceLinkData> = ConcurrentLinkedQueue()

    override val parent: EmbraceSpan?
        get() = parentContext.getEmbraceSpan(openTelemetry)

    override val spanContext: SpanContext?
        get() = sdkSpan?.spanContext

    override val traceId: String?
        get() = spanContext?.traceId

    override val spanId: String?
        get() = spanContext?.spanId

    override val isRecording: Boolean
        get() = sdkSpan?.isRecording() ?: false

    override fun start(startTimeMs: Long?): Boolean {
        if (!started()) {
            val timestampMs = startTimeMs ?: fakeClock.now()
            sdkSpan = createFakeKotlinSdkSpan(timestampMs)
            spanStartTimeMs = timestampMs
        }
        return true
    }

    private fun createFakeKotlinSdkSpan(timestampMs: Long): Span = FakeSpan(
        name = name,
        spanContext = openTelemetry.spanContextFactory.create(
            traceId = parent?.traceId ?: openTelemetry.tracingIdFactory.generateTraceIdBytes().toHexString(),
            spanId = openTelemetry.tracingIdFactory.generateSpanIdBytes().toHexString(),
            traceFlags = openTelemetry.traceFlagsFactory.default,
            traceState = openTelemetry.traceStateFactory.default,
        ),
        startTimestamp = timestampMs.millisToNanos(),
        parent = parentContext.getEmbraceSpan(openTelemetry)?.spanContext
            ?: openTelemetry.spanContextFactory.invalid
    )

    override fun stop(errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        if (isRecording) {
            this.errorCode = errorCode
            if (errorCode != null) {
                status = StatusData.Error(null)
            }

            if (status is StatusData.Error) {
                val error = errorCode?.fromErrorCode() ?: ErrorCodeAttribute.Failure
                setSystemAttribute(error.key.name, error.value)
            }

            val timestamp = endTimeMs ?: fakeClock.now()

            // Create and end a real span using the original tracer to ensure it gets exported
            otelSpanStartArgs?.startSpan(spanStartTimeMs ?: fakeClock.now())?.end(timestamp.millisToNanos())

            checkNotNull(sdkSpan).end(timestamp.millisToNanos())

            spanEndTimeMs = timestamp
        }
        return true
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>): Boolean {
        events.add(
            EmbraceSpanEvent.create(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: fakeClock.now(),
                attributes = attributes
            )
        )
        return true
    }

    override fun recordException(exception: Throwable, attributes: Map<String, String>): Boolean =
        addEvent(InstrumentedConfigImpl.otelLimits.getExceptionEventName(), null, attributes)

    override fun addSystemEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        addEvent(name, timestampMs, attributes ?: emptyMap())

    override fun removeSystemEvents(type: EmbType): Boolean {
        events.removeAll { it.hasEmbraceAttribute(type) }
        return true
    }

    override fun getStartTimeMs(): Long? = spanStartTimeMs

    override fun addAttribute(key: String, value: String): Boolean {
        attributes[key] = value
        return true
    }

    override fun updateName(newName: String): Boolean {
        name = newName
        return true
    }

    override fun addLink(linkedSpanContext: SpanContext, attributes: Map<String, String>): Boolean {
        links.add(EmbraceLinkData(linkedSpanContext, attributes))
        return true
    }

    override fun addSystemLink(linkedSpanContext: SpanContext, type: LinkType, attributes: Map<String, String>): Boolean {
        links.add(EmbraceLinkData(linkedSpanContext, mutableMapOf(type.asPair()).apply { putAll(attributes) }))
        return true
    }

    override fun asNewContext(): Context? = sdkSpan?.let {
        openTelemetry.contextFactory.storeSpan(parentContext, it)
    }

    override fun snapshot(): io.embrace.android.embracesdk.internal.payload.Span? {
        return if (spanId == null) {
            null
        } else {
            io.embrace.android.embracesdk.internal.payload.Span(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parent?.spanId,
                name = name,
                startTimeNanos = spanStartTimeMs?.millisToNanos(),
                endTimeNanos = spanEndTimeMs?.millisToNanos(),
                status = status.toEmbracePayload(),
                events = events.map(EmbraceSpanEvent::toEmbracePayload),
                attributes = attributes.toEmbracePayload(),
                links = links.toList().map { it.toEmbracePayload() }
            )
        }
    }

    override fun hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean =
        attributes.hasEmbraceAttribute(embraceAttribute)

    override fun getSystemAttribute(key: String): String? = attributes[key]

    override fun setSystemAttribute(key: String, value: String) {
        addSystemAttribute(key, value)
    }

    override fun addSystemAttribute(key: String, value: String) {
        attributes[key] = value
    }

    override fun removeSystemAttribute(key: String) {
        attributes.remove(key)
    }

    override fun attributes(): Map<String, Any> = attributes.toMap()

    override fun name(): String = name

    override val spanKind: SpanKind = otelSpanStartArgs?.spanKind ?: SpanKind.INTERNAL

    override fun events(): List<SpanEvent> {
        throw UnsupportedOperationException()
    }

    override fun links(): List<Link> {
        throw UnsupportedOperationException()
    }

    private fun started(): Boolean = sdkSpan != null

    companion object {
        fun notStarted(): FakeEmbraceSdkSpan =
            FakeEmbraceSdkSpan(
                name = "not-started"
            )

        fun started(
            parent: EmbraceSdkSpan? = null,
            parentContext: Context = parent?.run { parent.asNewContext() } ?: fakeOpenTelemetry().contextFactory.root(),
            clock: FakeClock = FakeClock(),
        ): FakeEmbraceSdkSpan =
            FakeEmbraceSdkSpan(
                name = "started",
                parentContext = parentContext,
                fakeClock = clock,
            ).apply {
                start()
            }

        fun stopped(): FakeEmbraceSdkSpan =
            FakeEmbraceSdkSpan(
                name = "stopped"
            ).apply {
                start()
                stop()
            }

        @OptIn(IncubatingApi::class)
        fun sessionSpan(
            sessionId: String,
            startTimeMs: Long,
            lastHeartbeatTimeMs: Long?,
            endTimeMs: Long? = null,
            sessionProperties: Map<String, String>? = null,
            processIdentifier: String = "fake-process-id",
        ): FakeEmbraceSdkSpan =
            FakeEmbraceSdkSpan(
                name = "emb-session",
                type = EmbType.Ux.Session,
            ).apply {
                start(startTimeMs)
                sessionProperties?.forEach {
                    addSystemAttribute(
                        key = it.key.toEmbraceAttributeName(),
                        value = it.value
                    )
                }

                setSystemAttribute(SessionAttributes.SESSION_ID, sessionId)
                setSystemAttribute(embProcessIdentifier.name, processIdentifier)
                setSystemAttribute(embState.name, "foreground")
                setSystemAttribute(
                    embHeartbeatTimeUnixNano.name,
                    (lastHeartbeatTimeMs ?: this.spanStartTimeMs)!!.millisToNanos().toString()
                )
                if (endTimeMs != null) {
                    stop(endTimeMs = endTimeMs)
                }
            }
    }
}
