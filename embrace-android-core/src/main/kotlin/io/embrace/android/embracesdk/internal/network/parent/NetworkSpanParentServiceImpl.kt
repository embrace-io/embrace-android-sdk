package io.embrace.android.embracesdk.internal.network.parent

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardUserCode
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceObjectName
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.concurrent.OneShot
import io.embrace.android.embracesdk.internal.utils.concurrent.ReentrancyGuard
import io.embrace.android.embracesdk.network.NetworkSpanParent
import io.embrace.android.embracesdk.network.NetworkSpanParentProvider
import io.embrace.android.embracesdk.network.NetworkSpanParentToken
import io.embrace.android.embracesdk.network.NetworkSpanStartInfo
import io.embrace.android.embracesdk.network.http.HttpRequestInfo
import io.embrace.android.embracesdk.semconv.EmbNetworkSpanParentAttributes
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.kotlin.tracing.SpanContext

private const val MAX_REPORTED_CONFLICTS = 10
private const val CONFLICT_EVENT_NAME = "network-span-parent-replaced"
private const val CONFLICT_TELEMETRY_TYPE = "network_span_parent_conflict"
private const val TRACE_ID_LENGTH = 32
private const val SPAN_ID_LENGTH = 16

internal class NetworkSpanParentServiceImpl(
    private val spanService: SpanService,
    private val errorHandler: ErrorHandler,
    private val telemetryService: InternalTelemetryService,
    private val currentUserSessionIdProvider: () -> String,
) : NetworkSpanParentService {

    private val conflictTracker = NetworkSpanParentConflictTracker(MAX_REPORTED_CONFLICTS)

    private val reportRejectedOnce = OneShot()

    private val reportInvalidDecisionOnce = OneShot()

    // so that a request the app's provider makes isn't decided by it again
    private val providerGuard = ReentrancyGuard()

    private val lock = Any()

    @Volatile
    private var active: ActiveParent? = null

    @Volatile
    private var appProvider: NetworkSpanParentProvider? = null

    override fun setAppProvider(provider: NetworkSpanParentProvider) {
        appProvider = provider
    }

    override fun appDecision(request: HttpRequestInfo): NetworkSpanParent = decide(request, active)

    // a stopped span may no longer be tracked, so its span context alone wouldn't be rejected
    override fun set(span: EmbraceSpan): NetworkSpanParentToken = set(span.spanContext?.takeIf { span.isRecording })

    override fun set(spanContext: SpanContext?): NetworkSpanParentToken {
        if (spanContext == null || !spanContext.isValid) {
            return rejectParent()
        }

        val trackedSpan = spanService.getSpan(spanContext.spanId)
        if (trackedSpan?.isRecording == false) {
            return rejectParent()
        }

        val parent = ActiveParent(
            spanContext = spanContext,
            trackedSpan = trackedSpan,
            userSessionId = currentUserSessionIdProvider(),
            name = (trackedSpan as? EmbraceSdkSpan)?.name(),
        )

        val replaced: ActiveParent?
        synchronized(lock) {
            val current = active
            if (current != null && current.isSameSpan(spanContext)) {
                return current
            }
            replaced = current
            active = parent
        }

        if (replaced != null) {
            reportConflict(replaced, parent)
        }

        // a span that stops between the check above and becoming the parent is never seen by onSpanStopped
        if (trackedSpan?.isRecording == false) {
            clearIfActive(parent)
        }
        return parent
    }

    override fun clear(token: NetworkSpanParentToken): Boolean = clearIfActive(token)

    override fun clear() {
        synchronized(lock) {
            active = null
        }
    }

    override fun currentParent(): NetworkSpanParent? = active?.parent

    override fun onSpanStopped(spanId: String) {
        val parent = active ?: return
        if (parent.trackedSpanId == spanId) {
            clearIfActive(parent)
        }
    }

    override fun onUserSessionEnded(userSessionId: String) {
        conflictTracker.reset()
        val parent = active ?: return
        if (parent.userSessionId == userSessionId || parent.userSessionId.isEmpty()) {
            clearIfActive(parent)
        }
    }

    /**
     * Returns the app provider's decision, or [NetworkSpanParent.USE_DEFAULT] if there is none, it fails, returns null or invalid IDs,
     * or it is already running on this thread. The provider is given the network span parent, never the traceparent header.
     */
    private fun decide(request: HttpRequestInfo, active: ActiveParent?): NetworkSpanParent {
        val appProvider = appProvider ?: return NetworkSpanParent.USE_DEFAULT
        val info = NetworkSpanStartInfoImpl(request, active)
        return providerGuard.runExclusive(onReentry = { NetworkSpanParent.USE_DEFAULT }) {
            errorHandler.guardSdkCode(SdkErrorType.NetworkSpanParentProviderFail, NetworkSpanParent.USE_DEFAULT) {
                errorHandler.guardUserCode { checked(appProvider.getNetworkSpanParent(info)) }
            }
        }
    }

    /**
     * Returns [decision] if the SDK built it, a copy if the app implemented it, or [NetworkSpanParent.USE_DEFAULT] if it's null or invalid.
     */
    private fun checked(decision: NetworkSpanParent?): NetworkSpanParent {
        // a NetworkSpanParentProvider implemented in Java can return null
        if (decision == null) {
            return NetworkSpanParent.USE_DEFAULT
        }
        if (decision === NetworkSpanParent.NO_PARENT || decision === NetworkSpanParent.USE_DEFAULT) {
            return decision
        }
        // each ID is read once, inside the provider's guard, so an app's getter can't throw or change once this returns
        val traceId = decision.traceId?.takeIf { it.isValidId(TRACE_ID_LENGTH) }
        val spanId = decision.spanId?.takeIf { it.isValidId(SPAN_ID_LENGTH) }
        if (traceId == null || spanId == null) {
            reportInvalidDecisionOnce {
                errorHandler.log("Ignored an invalid span context from the network span parent provider")
            }
            return NetworkSpanParent.USE_DEFAULT
        }
        return if (decision.javaClass === NetworkSpanParent.NO_PARENT.javaClass) decision else NetworkSpanParent.parent(traceId, spanId)
    }

    private fun String?.isValidId(length: Int): Boolean =
        this != null && this.length == length && all { it in '0'..'9' || it in 'a'..'f' } && any { it != '0' }

    private fun rejectParent(): NetworkSpanParentToken {
        reportRejectedOnce {
            errorHandler.log("Rejected an invalid or ended network span parent")
        }
        return NoopNetworkSpanParentToken
    }

    private fun reportConflict(replaced: ActiveParent, replacing: ActiveParent) {
        if (!conflictTracker.claimReport(replaced.label, replacing.label)) {
            telemetryService.trackAppliedLimit(CONFLICT_TELEMETRY_TYPE, AppliedLimitType.DROP)
            return
        }
        errorHandler.log("Network span parent '${replaced.label}' replaced by '${replacing.label}' while still active")
        val replacedSpanId = replaced.trackedSpanId
        if (replacedSpanId != null && conflictTracker.claimEvent(replacedSpanId)) {
            val replacedSpan = spanService.getSpan(replacedSpanId) as? EmbraceSdkSpan
            if (replacedSpan?.isRecording == true) {
                replacedSpan.addSystemEvent(
                    name = CONFLICT_EVENT_NAME.toEmbraceObjectName(),
                    timestampMs = null,
                    attributes = buildMap {
                        put(EmbNetworkSpanParentAttributes.EMB_REPLACED_BY_SPAN_ID, replacing.spanContext.spanId)
                        replacing.name?.let { put(EmbNetworkSpanParentAttributes.EMB_REPLACED_BY_NAME, it) }
                    },
                )
            }
        }
    }

    private fun clearIfActive(token: NetworkSpanParentToken): Boolean {
        if (active !== token) {
            return false
        }
        synchronized(lock) {
            if (active !== token) {
                return false
            }
            active = null
            return true
        }
    }

    private fun ActiveParent.isSameSpan(other: SpanContext): Boolean =
        spanContext.traceId == other.traceId && spanContext.spanId == other.spanId

    private class ActiveParent(
        val spanContext: SpanContext,
        val trackedSpan: EmbraceSpan?,
        val userSessionId: String,
        val name: String?,
    ) : NetworkSpanParentToken {
        val trackedSpanId: String? = trackedSpan?.let { spanContext.spanId }
        val label: String = name ?: spanContext.spanId
        val parent: NetworkSpanParent = NetworkSpanParent.parent(spanContext)
    }

    private class NetworkSpanStartInfoImpl(
        override val request: HttpRequestInfo,
        private val active: ActiveParent?,
    ) : NetworkSpanStartInfo {
        override val currentParent: NetworkSpanParent? get() = active?.parent
        override val currentParentSpan: EmbraceSpan? get() = active?.trackedSpan
        override val currentParentSpanContext: SpanContext? get() = active?.spanContext
    }
}

/**
 * Tracks which network span parent conflicts have been reported in the current user session, bounded by [maxReportedConflicts].
 */
private class NetworkSpanParentConflictTracker(private val maxReportedConflicts: Int) {
    private val reportedConflicts = HashSet<Pair<String, String>>()
    private val spansWithConflictEvent = HashSet<String>()

    private val lock = Any()

    /**
     * Returns true if this (replaced, replacing) label pair has not been reported yet and the limit allows reporting it.
     */
    fun claimReport(replaced: String, replacing: String): Boolean = synchronized(lock) {
        reportedConflicts.size < maxReportedConflicts && reportedConflicts.add(replaced to replacing)
    }

    /**
     * Returns true if the span has not yet received a conflict event in the current user session.
     */
    fun claimEvent(spanId: String): Boolean = synchronized(lock) {
        spansWithConflictEvent.add(spanId)
    }

    fun reset() {
        synchronized(lock) {
            reportedConflicts.clear()
            spansWithConflictEvent.clear()
        }
    }
}
