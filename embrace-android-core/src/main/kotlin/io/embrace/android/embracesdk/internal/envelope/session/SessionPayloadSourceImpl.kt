package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.session.captureDataSafely
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanSink

internal class SessionPayloadSourceImpl(
    private val symbolMapProvider: () -> Map<String, String>?,
    private val spanSink: SpanSink,
    private val currentSessionSpan: CurrentSessionSpan,
    private val spanRepository: SpanRepository,
    private val otelPayloadMapper: OtelPayloadMapper,
    private val processStateService: ProcessStateService,
    private val clock: Clock,
    private val logger: EmbLogger,
) : SessionPayloadSource {

    override fun getSessionPayload(
        endType: SessionSnapshotType,
        startNewSession: Boolean,
        crashId: String?,
    ): SessionPayload {
        val sharedLibSymbolMapping = captureDataSafely(logger, symbolMapProvider)
        val isCacheAttempt = endType == SessionSnapshotType.PERIODIC_CACHE
        val includeSnapshots = endType != SessionSnapshotType.JVM_CRASH

        if (!endType.forceQuit && processStateService.isInBackground) {
            spanRepository.autoTerminateSpans(clock.now())
        }

        // Snapshots should only be included if the process is expected to last beyond the current session
        val snapshots: List<Span>? = if (includeSnapshots) {
            retrieveSpanSnapshots(isCacheAttempt)
        } else {
            emptyList()
        }

        // Ensure the span retrieving is last as that potentially ends the session span, which effectively ends the session
        val spans: List<Span>? = retrieveSpanData(isCacheAttempt, startNewSession, crashId)

        return SessionPayload(
            spans = spans,
            spanSnapshots = snapshots,
            sharedLibSymbolMapping = sharedLibSymbolMapping
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
                    otelPayloadMapper.record()
                    val spans = currentSessionSpan.endSession(
                        startNewSession = startNewSession,
                        appTerminationCause = appTerminationCause
                    )
                    spans.map(EmbraceSpanData::toEmbracePayload)
                }

                else -> spanSink.completedSpans()
                    .map(EmbraceSpanData::toEmbracePayload)
                    .plus(otelPayloadMapper.snapshotSpans())
            }
        }
        return spans
    }

    private fun retrieveSpanSnapshots(isCacheAttempt: Boolean) = captureDataSafely(logger) {
        // Only snapshot session spans if we are caching an in-progress session payload
        spanRepository.getActiveSpans()
            .filter { isCacheAttempt || !it.hasFixedAttribute(EmbType.Ux.Session) }
            .mapNotNull { it.snapshot() }
    }
}
