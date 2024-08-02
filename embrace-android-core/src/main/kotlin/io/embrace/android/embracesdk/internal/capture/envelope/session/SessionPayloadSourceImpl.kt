package io.embrace.android.embracesdk.internal.capture.envelope.session

import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.envelope.session.SessionPayloadSource
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.session.captureDataSafely
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanSink

public class SessionPayloadSourceImpl(
    private val symbolMapProvider: () -> Map<String, String>?,
    private val spanSink: SpanSink,
    private val currentSessionSpan: CurrentSessionSpan,
    private val spanRepository: SpanRepository,
    private val otelPayloadMapper: OtelPayloadMapper,
    private val logger: EmbLogger
) : SessionPayloadSource {

    override fun getSessionPayload(endType: SessionSnapshotType, startNewSession: Boolean, crashId: String?): SessionPayload {
        val sharedLibSymbolMapping = captureDataSafely(logger, symbolMapProvider)

        // Ensure the span retrieving is last as that potentially ends the session span, which effectively ends the session
        val snapshots: List<Span>? = retrieveSpanSnapshots()
        val spans: List<Span>? = retrieveSpanData(endType, startNewSession, crashId)

        return SessionPayload(
            spans = spans,
            spanSnapshots = snapshots,
            sharedLibSymbolMapping = sharedLibSymbolMapping
        )
    }

    private fun retrieveSpanData(endType: SessionSnapshotType, startNewSession: Boolean, crashId: String?): List<Span>? {
        val cacheAttempt = endType == SessionSnapshotType.PERIODIC_CACHE

        val spans: List<Span>? = captureDataSafely(logger) {
            val result = when {
                !cacheAttempt -> {
                    val appTerminationCause = when {
                        crashId != null -> AppTerminationCause.Crash
                        else -> null
                    }
                    val spans = currentSessionSpan.endSession(
                        startNewSession = startNewSession,
                        appTerminationCause = appTerminationCause
                    )
                    spans.map(EmbraceSpanData::toNewPayload)
                }

                else -> spanSink.completedSpans().map(EmbraceSpanData::toNewPayload)
            }
            // add spans that need mapping to OTel if the payload is capturing spans.
            result.plus(otelPayloadMapper.getSessionPayload(endType, crashId))
        }
        return spans
    }

    private fun retrieveSpanSnapshots() = captureDataSafely(logger) {
        spanRepository.getActiveSpans().mapNotNull { it.snapshot() }
    }
}
