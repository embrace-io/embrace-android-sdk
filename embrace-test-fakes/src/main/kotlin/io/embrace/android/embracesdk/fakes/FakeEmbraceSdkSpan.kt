package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.session.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.attrs.asPair
import io.embrace.android.embracesdk.internal.otel.attrs.embHeartbeatTimeUnixNano
import io.embrace.android.embracesdk.internal.otel.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.otel.attrs.embState
import io.embrace.android.embracesdk.internal.otel.config.USE_KOTLIN_SDK
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute.Failure.fromErrorCode
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceLinkData
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.embrace.android.embracesdk.internal.otel.spans.getOrCreateSpanKey
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.toOtelKotlin
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.context.toOtelJavaContext
import io.embrace.opentelemetry.kotlin.context.toOtelJavaContextKey
import io.embrace.opentelemetry.kotlin.context.toOtelKotlinContext
import io.embrace.opentelemetry.kotlin.creator.ObjectCreator
import io.embrace.opentelemetry.kotlin.tracing.data.StatusData
import io.embrace.opentelemetry.kotlin.tracing.ext.toOtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import java.util.concurrent.ConcurrentLinkedQueue

@OptIn(ExperimentalApi::class)
class FakeEmbraceSdkSpan(
    private val useKotlinSdk: Boolean = USE_KOTLIN_SDK,
    private val objectCreator: ObjectCreator = if (useKotlinSdk) {
        fakeObjectCreator
    } else {
        fakeCompatObjectCreator
    },
    var name: String = "fake-span",
    var parentContext: Context = objectCreator.context.root(),
    val type: EmbType = EmbType.Performance.Default,
    val internal: Boolean = false,
    val private: Boolean = internal,
    override val autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    private val fakeClock: FakeClock = FakeClock(),
) : EmbraceSdkSpan {

    private var sdkSpan: Span? = null
    private var javaSdkSpan: FakeOtelJavaSpan? = null
    var spanStartTimeMs: Long? = null
    var spanEndTimeMs: Long? = null
    override var status: StatusData = StatusData.Unset
    var errorCode: ErrorCode? = null
    val attributes: MutableMap<String, String> = mutableMapOf(type.asPair())
    val events: ConcurrentLinkedQueue<EmbraceSpanEvent> = ConcurrentLinkedQueue()
    val links: ConcurrentLinkedQueue<EmbraceLinkData> = ConcurrentLinkedQueue()

    override val parent: EmbraceSpan?
        get() = parentContext.getEmbraceSpan(objectCreator)

    override val spanContext: OtelJavaSpanContext?
        get() = if (useKotlinSdk) {
            sdkSpan?.spanContext?.toOtelJavaSpanContext()
        } else {
            javaSdkSpan?.spanContext
        }

    override val traceId: String?
        get() = spanContext?.traceId

    override val spanId: String?
        get() = spanContext?.spanId

    override val isRecording: Boolean
        get() = if (useKotlinSdk) {
            sdkSpan?.isRecording() ?: false
        } else {
            javaSdkSpan?.isRecording ?: false
        }

    override fun start(): Boolean = start(startTimeMs = null)

    override fun start(startTimeMs: Long?): Boolean {
        if (!started()) {
            val timestampMs = startTimeMs ?: fakeClock.now()
            if (useKotlinSdk) {
                sdkSpan = createFakeKotlinSdkSpan(timestampMs)
            } else {
                javaSdkSpan = FakeOtelJavaSpan(parentContext = parentContext)
            }
            spanStartTimeMs = timestampMs
        }
        return true
    }

    private fun createFakeKotlinSdkSpan(timestampMs: Long): Span = FakeSpan(
        name = name,
        spanContext = objectCreator.spanContext.create(
            traceId = parent?.traceId ?: objectCreator.idCreator.generateTraceId(),
            spanId = objectCreator.idCreator.generateSpanId(),
            traceFlags = objectCreator.traceFlags.default,
            traceState = objectCreator.traceState.default,
        ),
        startTimestamp = timestampMs.millisToNanos(),
        parent = parentContext.getEmbraceSpan(objectCreator)?.spanContext?.toOtelKotlin()
            ?: objectCreator.spanContext.invalid
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
            if (useKotlinSdk) {
                checkNotNull(sdkSpan).end(timestamp.millisToNanos())
            } else {
                checkNotNull(javaSdkSpan).recording = false
            }

            spanEndTimeMs = timestamp
        }
        return true
    }

    override fun addEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean {
        events.add(
            EmbraceSpanEvent.create(
                name = name,
                timestampMs = timestampMs?.normalizeTimestampAsMillis() ?: fakeClock.now(),
                attributes = attributes
            )
        )
        return true
    }

    override fun recordException(exception: Throwable, attributes: Map<String, String>?): Boolean =
        addEvent(InstrumentedConfigImpl.otelLimits.getExceptionEventName(), null, attributes)

    override fun addSystemEvent(name: String, timestampMs: Long?, attributes: Map<String, String>?): Boolean =
        addEvent(name, timestampMs, attributes)

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

    override fun addLink(linkedSpanContext: OtelJavaSpanContext, attributes: Map<String, String>?): Boolean {
        links.add(EmbraceLinkData(linkedSpanContext.toOtelKotlin(), attributes ?: emptyMap()))
        return true
    }

    override fun addSystemLink(linkedSpanContext: SpanContext, type: LinkType, attributes: Map<String, String>): Boolean {
        links.add(EmbraceLinkData(linkedSpanContext, mutableMapOf(type.asPair()).apply { putAll(attributes) }))
        return true
    }

    override fun asNewContext(): Context? = if (useKotlinSdk) {
        sdkSpan?.let {
            objectCreator.context.storeSpan(parentContext, it)
        }
    } else {
        javaSdkSpan?.let {
            parentContext.toOtelJavaContext().with(this).with(it).toOtelKotlinContext()
        }
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

    override val spanKind: SpanKind = SpanKind.INTERNAL

    override fun events(): List<SpanEvent> {
        throw UnsupportedOperationException()
    }

    override fun links(): List<Link> {
        throw UnsupportedOperationException()
    }

    override fun storeInContext(context: OtelJavaContext): OtelJavaContext {
        val spanKey = getOrCreateSpanKey(objectCreator)
        return context.with(spanKey.toOtelJavaContextKey(), this)
    }

    private fun started(): Boolean = if (useKotlinSdk) {
        sdkSpan != null
    } else {
        javaSdkSpan != null
    }

    companion object {
        fun notStarted(): FakeEmbraceSdkSpan =
            FakeEmbraceSdkSpan(
                name = "not-started"
            )

        fun started(
            parent: EmbraceSdkSpan? = null,
            parentContext: Context = parent?.run { parent.asNewContext() } ?: fakeObjectCreator.context.root(),
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
                        key = it.key.toSessionPropertyAttributeName(),
                        value = it.value
                    )
                }

                setSystemAttribute(SessionIncubatingAttributes.SESSION_ID.key, sessionId)
                setSystemAttribute(embProcessIdentifier.name, processIdentifier)
                setSystemAttribute(embState.name, "foreground")
                setSystemAttribute(
                    embHeartbeatTimeUnixNano.name,
                    (lastHeartbeatTimeMs ?: this.spanStartTimeMs)!!.millisToNanos().toString()
                )
                if (endTimeMs != null) {
                    stop(endTimeMs)
                }
            }
    }
}
