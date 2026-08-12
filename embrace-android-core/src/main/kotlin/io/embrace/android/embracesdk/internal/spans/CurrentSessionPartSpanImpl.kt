package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior.Companion.DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceLinkData
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.tracing.Tracer
import java.util.concurrent.atomic.AtomicInteger

internal class CurrentSessionPartSpanImpl(
    private val openTelemetryClock: Clock,
    private val telemetryService: TelemetryService,
    private val spanRepository: SpanRepository,
    private val tracerSupplier: Provider<Tracer>,
    private val openTelemetrySupplier: Provider<OpenTelemetry>,
    private val embraceSpanFactorySupplier: Provider<EmbraceSpanFactory>,
    private val uuidSource: UuidSource,
    private val otelBehaviorSupplier: Provider<OtelBehavior?>,
    private val customBreadcrumbLimitSupplier: Provider<Int>,
) : CurrentSessionPartSpan {

    /**
     * Guards initialization and starting of new sessions. This is not used to fully guard the sessionState which is read during
     * [canStartNewSpan] and [spanStopCallback] without guards to avoid creating a bottleneck.
     */
    private val sessionTransitionLock = Any()

    @Volatile
    private var initialized: Boolean = false

    /**
     * Encapsulation of the current session part span (if there is one) and its trace counts.
     */
    @Volatile
    private var sessionPartState: SessionPartState? = null

    /**
     * The link data used to join a new session part span to the one that preceded it
     */
    @Volatile
    private var lastSessionPartLink: EmbraceLinkData? = null

    override fun initializeService(sdkInitStartTimeMs: Long) {
        if (!initialized) {
            synchronized(sessionTransitionLock) {
                if (!initialized) {
                    ensureInitialSessionPartExists { sdkInitStartTimeMs }
                    initialized = sessionPartState != null
                }
            }
        }
    }

    override fun initialized(): Boolean = initialized

    /**
     * Creating a new Span is only possible if the current session part span is active, the parent has already been started, and the
     * session trace limit for the span's budget has not been reached. Network request spans draw from their own budget so that a
     * network-heavy app cannot starve the rest of the SDK's instrumentation. Once this method returns true, a new span is assumed to
     * have been created and will be counted as such towards the limits, so make sure there's no case afterwards where a Span is not
     * created.
     */
    override fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean, type: EmbType): Boolean {
        val state = sessionPartState ?: return false
        if (!state.isReady || (parent != null && parent.spanId == null)) {
            return false
        }

        val behavior = otelBehaviorSupplier()
        return when {
            !internal -> checkCount(
                state.traceCount,
                behavior?.getMaxCustomSpansPerSessionPart() ?: DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART,
                SPAN_LIMIT_TYPE,
            )

            type == EmbType.Performance.Network -> checkCount(
                state.networkTraceCount,
                behavior?.getMaxNetworkSpansPerSessionPart() ?: DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART,
                NETWORK_SPAN_LIMIT_TYPE,
            )

            else -> checkCount(
                state.internalTraceCount,
                behavior?.getMaxInternalSpansPerSessionPart() ?: DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART,
                SPAN_LIMIT_TYPE,
            )
        }
    }

    /**
     * Breadcrumbs get their own budget so that a flood of other telemetry can't starve them, and vice versa. Both
     * limits are read on each call so a remote config change takes effect immediately.
     */
    override fun canAddEvent(isBreadcrumb: Boolean): Boolean {
        val state = sessionPartState ?: return false
        if (!state.isReady) {
            return false
        }

        return if (isBreadcrumb) {
            checkCount(state.breadcrumbCount, customBreadcrumbLimitSupplier(), SPAN_EVENT_LIMIT_TYPE)
        } else {
            checkCount(
                state.eventCount,
                otelBehaviorSupplier()?.getMaxSpanEventsPerSessionPart() ?: DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART,
                SPAN_EVENT_LIMIT_TYPE,
            )
        }
    }

    private fun checkCount(counter: AtomicInteger, limit: Int, limitType: String): Boolean {
        return if (counter.get() >= limit) {
            telemetryService.trackAppliedLimit(limitType, AppliedLimitType.DROP)
            false
        } else {
            counter.getAndIncrement() < limit
        }
    }

    override fun getId(): String = sessionPartState?.sessionPartId ?: ""

    override fun spanStopCallback(spanId: String) {
        val state = sessionPartState
        val currentSessionPartSpan = state?.span
        val spanToStop = spanRepository.getEmbraceSpan(spanId)

        if (currentSessionPartSpan != spanToStop) {
            val linkAttrs = state?.cachedPartLinkAttrs() ?: emptyMap()
            spanToStop?.spanContext?.let { spanToStopContext ->
                if (currentSessionPartSpan != null) {
                    currentSessionPartSpan.addSystemLink(
                        linkedSpanContext = spanToStopContext,
                        type = LinkType.EndedIn,
                        attributes = linkAttrs,
                    )
                    if (spanToStop.hasEmbraceAttribute(EmbType.State)) {
                        currentSessionPartSpan.addSystemLink(
                            linkedSpanContext = spanToStopContext,
                            type = LinkType.State,
                            attributes = linkAttrs,
                        )
                    }
                }
            }

            currentSessionPartSpan?.spanContext?.let { sessionPartSpanContext ->
                spanToStop?.addSystemLink(
                    linkedSpanContext = sessionPartSpanContext,
                    type = LinkType.EndSessionPart,
                    attributes = linkAttrs,
                )
            }
        }
    }

    override fun readySession(): Boolean {
        ensureInitialSessionPartExists { openTelemetryClock.now().nanosToMillis() }
        return sessionPartSpanReady()
    }

    override fun endSession(
        startNewSession: Boolean,
        appTerminationCause: AppTerminationCause?,
    ): List<Span> {
        synchronized(sessionTransitionLock) {
            val endingState = sessionPartState ?: return emptyList()
            val endingSessionPartSpan = endingState.span
            return if (endingSessionPartSpan.isRecording) {
                // Right now, session part spans don't survive native crashes and sudden process terminations,
                // so telemetry will not be recorded in those cases, for now.
                val telemetryAttributes = telemetryService.getAndClearTelemetryAttributes()

                telemetryAttributes.forEach {
                    endingSessionPartSpan.addAttribute(it.key, it.value)
                }

                if (appTerminationCause == null) {
                    endingSessionPartSpan.stop()
                    lastSessionPartLink = endingSessionPartSpan.spanContext?.let {
                        EmbraceLinkData(it, endingState.cachedPartLinkAttrs())
                    }
                    spanRepository.clearCompletedEmbraceSpans()
                    sessionPartState = if (startNewSession) {
                        startSessionPartSpan(openTelemetryClock.now().nanosToMillis())
                    } else {
                        null
                    }
                } else {
                    val crashTime = openTelemetryClock.now().nanosToMillis()
                    spanRepository.failActiveEmbraceSpans(crashTime)
                    endingSessionPartSpan.setSystemAttribute(
                        appTerminationCause.key,
                        appTerminationCause.value,
                    )
                    endingSessionPartSpan.stopWithErrorCode(errorCode = ErrorCodeAttribute.Failure, endTimeMs = crashTime)
                }
                spanRepository.flushOtelSpans()
            } else {
                emptyList()
            }
        }
    }

    override fun current(): EmbraceSdkSpan? = sessionPartState?.span

    /**
     * Creates the current session part span if one does not already exist.
     */
    private inline fun ensureInitialSessionPartExists(startTimeMs: () -> Long) {
        if (sessionPartState == null) {
            synchronized(sessionTransitionLock) {
                if (sessionPartState == null) {
                    sessionPartState = startSessionPartSpan(startTimeMs())
                }
            }
        }
    }

    /**
     * This method should always be used when starting a new session part span. It creates a UUID for the session part and
     * puts it in the span. This is the one true place for creating a new session part and its persisted metadata.
     */
    private fun startSessionPartSpan(startTimeMs: Long): SessionPartState {
        val sessionPartId = uuidSource.createUuid()
        val span = embraceSpanFactorySupplier().create(
            OtelSpanStartArgs(
                name = "session",
                type = EmbType.Ux.Session,
                internal = true,
                private = false,
                tracer = tracerSupplier(),
                openTelemetry = openTelemetrySupplier(),
            ),
        ).apply {
            start(startTimeMs = startTimeMs)
            setSystemAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, sessionPartId)
            lastSessionPartLink?.let {
                addSystemLink(
                    linkedSpanContext = it.spanContext,
                    type = LinkType.PreviousSessionPart,
                    attributes = it.attributes,
                )
            }
        }
        return SessionPartState(span, sessionPartId)
    }

    private fun sessionPartSpanReady() = sessionPartState?.isReady == true

    /**
     * Encapsulates the current session part span and the current trace counts for limit enforcement.
     */
    private class SessionPartState(val span: EmbraceSdkSpan, val sessionPartId: String) {
        val traceCount: AtomicInteger = AtomicInteger(0)
        val internalTraceCount: AtomicInteger = AtomicInteger(0)
        val networkTraceCount: AtomicInteger = AtomicInteger(0)
        val eventCount: AtomicInteger = AtomicInteger(0)
        val breadcrumbCount: AtomicInteger = AtomicInteger(0)

        /**
         * Memoized link attributes for this session part. Only set once the user session attributes have been populated on the
         * span, after which the attributes referenced by [partLinkAttrs] no longer change for the lifetime of the part.
         */
        @Volatile
        var linkAttrs: Map<String, String>? = null

        val isReady: Boolean get() = span.isRecording
    }

    private fun SessionPartState.cachedPartLinkAttrs(): Map<String, String> {
        linkAttrs?.let { return it }
        val attrs = span.partLinkAttrs()
        if (attrs.containsKey(EmbSessionAttributes.EMB_USER_SESSION_ID)) {
            linkAttrs = attrs
        }
        return attrs
    }

    /**
     * Attributes for a span link that references the session part represented by this span.
     */
    private fun EmbraceSdkSpan.partLinkAttrs(): Map<String, String> = buildMap {
        getSystemAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID)?.let {
            put(EmbSessionAttributes.EMB_SESSION_PART_ID, it)
        }
        getSystemAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID)?.let {
            put(EmbSessionAttributes.EMB_USER_SESSION_ID, it)
        }
    }

    companion object {
        private const val SPAN_LIMIT_TYPE = "span"
        private const val NETWORK_SPAN_LIMIT_TYPE = "network_span"
        private const val SPAN_EVENT_LIMIT_TYPE = "span_event"
    }
}
