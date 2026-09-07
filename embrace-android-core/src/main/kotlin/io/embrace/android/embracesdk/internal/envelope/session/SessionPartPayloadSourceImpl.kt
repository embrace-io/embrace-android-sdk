package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.captureDataSafely
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan

internal class SessionPartPayloadSourceImpl(
    private val symbolMap: Map<String, String>?,
    private val currentSessionPartSpan: CurrentSessionPartSpan,
    private val spanRepository: SpanRepository,
    private val otelPayloadMapper: OtelPayloadMapper?,
    private val processStateTracker: ProcessStateTracker,
    private val clock: Clock,
    private val logger: InternalLogger,
) : SessionPartPayloadSource {

    override fun getSessionPartPayload(
        endType: SessionPartSnapshotType,
        startNewSession: Boolean,
        crashId: String?,
    ): SessionPartPayload = collectSessionPart(endType, startNewSession, crashId, buildSnapshots = true)

    override fun endSessionPart(
        endType: SessionPartSnapshotType,
        startNewSession: Boolean,
        crashId: String?,
    ) {
        collectSessionPart(endType, startNewSession, crashId, buildSnapshots = false)
    }

    /**
     * Ends the session part, collecting its telemetry into a [SessionPartPayload].
     */
    private fun collectSessionPart(
        endType: SessionPartSnapshotType,
        startNewSession: Boolean,
        crashId: String?,
        buildSnapshots: Boolean,
    ): SessionPartPayload {
        val isCacheAttempt = endType == SessionPartSnapshotType.PERIODIC_CACHE
        val includeSnapshots = endType != SessionPartSnapshotType.JVM_CRASH

        // When a session part actually ends (not a periodic cache snapshot, which the dedicated
        // background sweep already covers), fail any in-flight spans that have exceeded their
        // timeout before the session part is finalized, regardless of app state.
        if (!isCacheAttempt) {
            spanRepository.stopTimedOutSpans(clock.now())
        }

        if (!endType.forceQuit && processStateTracker.getAppState() == ProcessState.BACKGROUND) {
            spanRepository.autoTerminateEmbraceSpans(clock.now())
        }

        // Snapshots should only be included if the process is expected to last beyond the current user session
        val snapshots: List<Span>? = when {
            !buildSnapshots -> null
            includeSnapshots -> retrieveSpanSnapshots(isCacheAttempt)
            else -> emptyList()
        }

        // Ensure the span retrieving is last as that potentially ends the session part span, which effectively ends the user session.
        // Its result goes unused when no payload is wanted, but its side effects - recording pending telemetry, stopping the session
        // part span and draining the completed spans - are required either way.
        val spans: List<Span>? = retrieveSpanData(isCacheAttempt, startNewSession, crashId)

        return SessionPartPayload(
            spans = spans,
            spanSnapshots = snapshots,
            sharedLibSymbolMapping = symbolMap,
        )
    }

    private fun retrieveSpanData(
        isCacheAttempt: Boolean,
        startNewSession: Boolean,
        crashId: String?,
    ): List<Span>? {
        val spans: List<Span>? = captureDataSafely(logger) {
            when {
                !isCacheAttempt -> {
                    val appTerminationCause = when {
                        crashId != null -> AppTerminationCause.Crash
                        else -> null
                    }
                    otelPayloadMapper?.record()
                    currentSessionPartSpan.endSession(
                        startNewSession = startNewSession,
                        appTerminationCause = appTerminationCause,
                    )
                }

                else -> spanRepository.completedOtelSpans()
                    .plus(otelPayloadMapper?.snapshotSpans() ?: emptyList())
            }
        }
        return spans
    }

    private fun retrieveSpanSnapshots(isCacheAttempt: Boolean) = captureDataSafely(logger) {
        // Only snapshot session part spans if we are caching an in-progress session payload
        spanRepository.getActiveEmbraceSpans()
            .filter { isCacheAttempt || !it.hasEmbraceAttribute(EmbType.Ux.Session) }
            .mapNotNull { it.snapshot() }
    }
}
