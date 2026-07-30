package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.datasource.SpanEvent
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.clock.normalizeTimestampAsMillis
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.tracing.Tracer

/**
 * Implementation of [SpanService].
 *
 * This can be instantiated cheaply before the SDK has started: the OTel SDK components it needs are resolved lazily through
 * [tracerSupplier] and [openTelemetrySupplier] when [initializeService] is called. The suppliers exist to break a dependency cycle -
 * the OTel SDK is itself constructed with a reference to this service - so they must not be resolved at construction time.
 *
 * Until the service is initialized, span creation is a no-op and calls to [recordCompletedSpan] are buffered, up to
 * [MAX_BUFFERED_CALLS] of them, so they can be replayed once the OTel SDK exists.
 */
class SpanServiceImpl(
    private val spanRepository: SpanRepository,
    private val dataValidator: DataValidator,
    private val canStartNewSpan: (parentSpan: EmbraceSpan?, internal: Boolean) -> Boolean,
    private val initCallback: (initTimeMs: Long) -> Unit,
    private val embraceSpanFactory: EmbraceSpanFactory,
    private val tracerSupplier: Provider<Tracer>,
    private val openTelemetrySupplier: Provider<OpenTelemetry>,
) : SpanService {

    /**
     * Makes [initializeService] idempotent. Deliberately a different lock to [bufferLock]: resolving the suppliers instantiates the
     * whole OTel SDK, and callers of [recordCompletedSpan] should not block for that long.
     */
    private val initLock = Any()

    /**
     * Guards [bufferedCalls], including the handover from buffering to recording directly: it is held while the buffer is replayed so
     * that a caller which has already observed [otelComponents] as null cannot add to the buffer after the replay has finished.
     */
    private val bufferLock = Any()

    /**
     * Buffered [recordCompletedSpan] calls awaiting replay. Only ever touched while holding [bufferLock].
     */
    private val bufferedCalls = ArrayDeque<BufferedRecordCompletedSpan>()

    /**
     * The OTel SDK components required to record spans, or null if the SDK has not started yet.
     */
    @Volatile
    private var otelComponents: OtelComponents? = null

    override fun initializeService(sdkInitStartTimeMs: Long) {
        if (otelComponents != null) {
            return
        }
        synchronized(initLock) {
            if (otelComponents != null) {
                return
            }
            val components = OtelComponents(
                tracer = tracerSupplier(),
                openTelemetry = openTelemetrySupplier(),
            )
            initCallback(sdkInitStartTimeMs)
            synchronized(bufferLock) {
                // replay before publishing, so that callers blocked on bufferLock record after the replay instead of racing it
                bufferedCalls.forEach { components.replay(it) }
                bufferedCalls.clear()
                otelComponents = components
            }
        }
    }

    override fun initialized(): Boolean = otelComponents != null

    override fun createSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        terminationMode: SpanTerminationMode,
    ): EmbraceSdkSpan {
        val components = otelComponents ?: return NoopEmbraceSdkSpan
        return if (name.isNotBlank() && canStartNewSpan(parent, internal)) {
            embraceSpanFactory.create(
                OtelSpanStartArgs(
                    name = dataValidator.truncateName(name, internal),
                    type = type,
                    internal = internal,
                    private = private,
                    tracer = components.tracer,
                    terminationMode = terminationMode,
                    parentCtx = (parent as? EmbraceSdkSpan)?.createContext(components.openTelemetry),
                    openTelemetry = components.openTelemetry,
                ),
            )
        } else {
            NoopEmbraceSdkSpan
        }
    }

    override fun createSpan(otelSpanStartArgs: OtelSpanStartArgs): EmbraceSdkSpan {
        val components = otelComponents ?: return NoopEmbraceSdkSpan
        return if (
            otelSpanStartArgs.initialSpanName.isNotBlank() &&
            canStartNewSpan(
                otelSpanStartArgs.parentContext.getEmbraceSpan(components.openTelemetry),
                otelSpanStartArgs.internal,
            )
        ) {
            embraceSpanFactory.create(otelSpanStartArgs)
        } else {
            NoopEmbraceSdkSpan
        }
    }

    override fun startSpan(
        name: String,
        parent: EmbraceSpan?,
        startTimeMs: Long?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        terminationMode: SpanTerminationMode,
    ): EmbraceSdkSpan {
        val newSpan = createSpan(
            name = name,
            parent = parent,
            type = type,
            internal = internal,
            private = private,
            terminationMode = terminationMode,
        )
        return when {
            newSpan.start(startTimeMs) -> newSpan
            else -> NoopEmbraceSdkSpan
        }
    }

    /**
     * No initialization check is needed here: before initialization [createSpan] returns [NoopEmbraceSdkSpan], whose start always
     * fails, so [code] is the only thing that ends up running.
     */
    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<SpanEvent>,
        terminationMode: SpanTerminationMode,
        code: () -> T,
    ): T {
        val returnValue: T
        val span = createSpan(
            name = name,
            parent = parent,
            type = type,
            internal = internal,
            private = private,
            terminationMode = terminationMode,
        )
        try {
            if (span.start()) {
                attributes.forEach { attribute ->
                    span.addAttribute(attribute.key, attribute.value)
                }
                events.forEach { event ->
                    span.addEvent(
                        event.name,
                        event.timestampNanos.nanosToMillis(),
                        event.attributes,
                    )
                }
            }
            returnValue = code()
            span.stop()
        } catch (t: Throwable) {
            span.stopWithErrorCode(ErrorCodeAttribute.Failure)
            throw t
        }

        return returnValue
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<SpanEvent>,
        errorCode: ErrorCodeAttribute?,
    ): Boolean {
        val components = otelComponents ?: synchronized(bufferLock) {
            otelComponents ?: return buffer(
                name = name,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                parent = parent,
                type = type,
                internal = internal,
                private = private,
                attributes = attributes,
                events = events,
                errorCode = errorCode,
            )
        }
        return components.record(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            parent = parent,
            type = type,
            internal = internal,
            private = private,
            attributes = attributes,
            events = events,
            errorCode = errorCode,
        )
    }

    override fun getSpan(spanId: String): EmbraceSpan? = spanRepository.getEmbraceSpan(spanId = spanId)

    /**
     * Saves a [recordCompletedSpan] call so that it can be replayed on initialization. Must be called while holding [bufferLock].
     *
     * Note that the span is not validated here - that is deferred to the replay - so a call that will ultimately be rejected still
     * reports success.
     */
    private fun buffer(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<SpanEvent>,
        errorCode: ErrorCodeAttribute?,
    ): Boolean {
        if (bufferedCalls.size >= MAX_BUFFERED_CALLS) {
            return false
        }
        bufferedCalls.add(
            BufferedRecordCompletedSpan(
                name = name,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                parent = parent,
                type = type,
                internal = internal,
                private = private,
                attributes = attributes,
                events = events,
                errorCode = errorCode,
            ),
        )
        return true
    }

    private fun OtelComponents.replay(buffered: BufferedRecordCompletedSpan): Boolean = record(
        name = buffered.name,
        startTimeMs = buffered.startTimeMs,
        endTimeMs = buffered.endTimeMs,
        parent = buffered.parent,
        type = buffered.type,
        internal = buffered.internal,
        private = buffered.private,
        attributes = buffered.attributes,
        events = buffered.events,
        errorCode = buffered.errorCode,
    )

    private fun OtelComponents.record(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        parent: EmbraceSpan?,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        attributes: Map<String, String>,
        events: List<SpanEvent>,
        errorCode: ErrorCodeAttribute?,
    ): Boolean {
        // callers may supply nanosecond timestamps - normalize before validating so that the check below compares like with like
        val validStartTimeMs = startTimeMs.normalizeTimestampAsMillis()
        val validEndTimeMs = endTimeMs.normalizeTimestampAsMillis()

        if (validStartTimeMs > validEndTimeMs) {
            return false
        }

        val validName = dataValidator.truncateName(name, internal)
        val validEvents = dataValidator.truncateEvents(events, internal)
        val validAttributes = dataValidator.truncateAttributes(attributes, internal)

        if (canStartNewSpan(parent, internal)) {
            val newSpan = embraceSpanFactory.create(
                OtelSpanStartArgs(
                    name = validName,
                    type = type,
                    internal = internal,
                    private = private,
                    tracer = tracer,
                    parentCtx = (parent as? EmbraceSdkSpan)?.createContext(openTelemetry),
                    openTelemetry = openTelemetry,
                ),
            )
            if (newSpan.start(validStartTimeMs)) {
                validAttributes.forEach {
                    newSpan.addAttribute(it.key, it.value)
                }
                validEvents.forEach {
                    newSpan.addEvent(it.name, it.timestampNanos.nanosToMillis(), it.attributes)
                }
                return newSpan.stopWithErrorCode(errorCode, endTimeMs)
            }
        }

        return false
    }

    /**
     * The OTel SDK components that this service needs in order to record spans. Held in a single immutable object so that they can
     * only be reached once the service has been initialized, and so that they are published atomically.
     */
    private class OtelComponents(
        val tracer: Tracer,
        val openTelemetry: OpenTelemetry,
    )

    /**
     * Represents a call to [SpanService.recordCompletedSpan] that can be saved and replayed later when the SDK is initialized.
     */
    private data class BufferedRecordCompletedSpan(
        val name: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val parent: EmbraceSpan?,
        val type: EmbType,
        val internal: Boolean,
        val private: Boolean,
        val attributes: Map<String, String>,
        val events: List<SpanEvent>,
        val errorCode: ErrorCodeAttribute?,
    )

    companion object {
        private const val MAX_BUFFERED_CALLS = 1000
    }
}
