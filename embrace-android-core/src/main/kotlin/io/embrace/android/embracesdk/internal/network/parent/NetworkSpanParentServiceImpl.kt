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
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.stripUrl
import io.embrace.android.embracesdk.internal.utils.concurrent.OneShot
import io.embrace.android.embracesdk.internal.utils.concurrent.ReentrancyGuard
import io.embrace.android.embracesdk.network.NetworkSpanParent
import io.embrace.android.embracesdk.network.NetworkSpanParentProvider
import io.embrace.android.embracesdk.network.NetworkSpanParentToken
import io.embrace.android.embracesdk.network.http.HttpRequestInfo
import io.embrace.android.embracesdk.semconv.EmbNetworkSpanParentAttributes
import io.opentelemetry.kotlin.tracing.SpanContext
import java.util.concurrent.TimeUnit

private const val MAX_REPORTED_CONFLICTS = 10
private const val CONFLICT_EVENT_NAME = "network-span-parent-replaced"
private const val CONFLICT_TELEMETRY_TYPE = "network_span_parent_conflict"
private const val SLOW_CALLBACK_NAME = "network_span_parent_provider"

internal class NetworkSpanParentServiceImpl(
    private val spanService: SpanService,
    private val errorHandler: ErrorHandler,
    private val telemetryService: InternalTelemetryService,
    slowProviderThresholdMs: Long,
    private val currentUserSessionIdProvider: () -> String,
) : NetworkSpanParentService {

    private val conflictTracker = NetworkSpanParentConflictTracker(MAX_REPORTED_CONFLICTS)

    private val slowProviderThresholdNs = TimeUnit.MILLISECONDS.toNanos(slowProviderThresholdMs)

    private val reportRejectedOnce = OneShot()

    private val reportInvalidDecisionOnce = OneShot()

    private val reportSlowProviderOnce = OneShot()

    // so that a request the app's provider makes isn't decided by it again
    private val providerGuard = ReentrancyGuard()

    private val lock = Any()

    @Volatile
    private var active: ActiveParent? = null

    @Volatile
    private var appProvider: NetworkSpanParentProvider? = null

    override val provider = NetworkSpanParentProvider { request, currentParent ->
        val active = active
        decide(request, active?.spanContext).orDefault {
            when {
                currentParent != null || active == null -> NetworkSpanParent.USE_DEFAULT
                else -> NetworkSpanParent.parent(active.spanContext)
            }
        }
    }

    override fun setAppProvider(provider: NetworkSpanParentProvider) {
        appProvider = provider
    }

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
            trackedSpanId = if (trackedSpan != null) spanContext.spanId else null,
            userSessionId = currentUserSessionIdProvider(),
            label = (trackedSpan as? EmbraceSdkSpan)?.name() ?: spanContext.spanId,
        )
        val replaced: ActiveParent?
        synchronized(lock) {
            replaced = active
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

    override fun clear(token: NetworkSpanParentToken) = clearIfActive(token)

    override fun currentParent(): SpanContext? = active?.spanContext

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
     * Returns the app provider's decision, or [NetworkSpanParent.USE_DEFAULT] if there is none, it fails, or it is already running on
     * this thread. The provider is given the network span parent, never the traceparent header.
     */
    private fun decide(request: HttpRequestInfo, networkSpanParent: SpanContext?): NetworkSpanParent {
        val appProvider = appProvider ?: return NetworkSpanParent.USE_DEFAULT
        val decision = providerGuard.runExclusive(onReentry = { NetworkSpanParent.USE_DEFAULT }) {
            val startNs = System.nanoTime()
            errorHandler.guardSdkCode(SdkErrorType.NetworkSpanParentProviderFail, NetworkSpanParent.USE_DEFAULT) {
                errorHandler.guardUserCode { appProvider.getNetworkSpanParent(request, networkSpanParent) }
            }.also {
                reportIfSlow(request, System.nanoTime() - startNs)
            }
        }
        return if (decision.spanContext?.isValid == false) {
            reportInvalidDecisionOnce {
                errorHandler.log("Ignored an invalid span context from the network span parent provider")
            }
            NetworkSpanParent.USE_DEFAULT
        } else {
            decision
        }
    }

    private fun reportIfSlow(request: HttpRequestInfo, durationNs: Long) {
        if (durationNs <= slowProviderThresholdNs) {
            return
        }
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        reportSlowProviderOnce {
            errorHandler.log("Network span parent provider took $durationMs ms for ${stripUrl(request.url)}")
        }
        telemetryService.trackSlowCallback(SLOW_CALLBACK_NAME, durationMs)
    }

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
                    attributes = mapOf(EmbNetworkSpanParentAttributes.EMB_NETWORK_SPAN_PARENT_REPLACED_BY to replacing.label),
                )
            }
        }
    }

    private fun clearIfActive(token: NetworkSpanParentToken) {
        if (active !== token) {
            return
        }
        synchronized(lock) {
            if (active === token) {
                active = null
            }
        }
    }

    private class ActiveParent(
        val spanContext: SpanContext,
        val trackedSpanId: String?,
        val userSessionId: String,
        val label: String,
    ) : NetworkSpanParentToken
}

/**
 * Returns this decision, or the one [default] returns when this is [NetworkSpanParent.USE_DEFAULT].
 */
internal inline fun <T : NetworkSpanParent?> T.orDefault(default: () -> T): T =
    if (this === NetworkSpanParent.USE_DEFAULT) default() else this

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
