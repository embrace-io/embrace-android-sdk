package io.embrace.android.embracesdk.capture.envelope.session

import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.session.captureDataSafely
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan

internal class SessionPayloadSourceImpl(
    private val nativeThreadSamplerService: NativeThreadSamplerService?,
    private val spanSink: SpanSink,
    private val currentSessionSpan: CurrentSessionSpan,
    private val spanRepository: SpanRepository,
    private val logger: EmbLogger,
    private val sessionPropertiesServiceProvider: Provider<SessionPropertiesService>
) : SessionPayloadSource {

    override fun getSessionPayload(endType: SessionSnapshotType): SessionPayload {
        val sharedLibSymbolMapping = captureDataSafely(logger) { nativeThreadSamplerService?.getNativeSymbols() }
        val snapshots = retrieveSpanSnapshotData()

        // Ensure the span retrieving is last as that potentially ends the session span, which effectively ends the session
        val spans = retrieveSpanData(endType)
        return SessionPayload(
            spans = spans,
            spanSnapshots = snapshots,
            sharedLibSymbolMapping = sharedLibSymbolMapping
        )
    }

    private fun retrieveSpanData(endType: SessionSnapshotType): List<Span>? = captureDataSafely(logger) {
        when (endType) {
            SessionSnapshotType.NORMAL_END -> {
                val flushedSpans = currentSessionSpan.endSession(null)
                sessionPropertiesServiceProvider().populateCurrentSession()
                flushedSpans
            }
            SessionSnapshotType.PERIODIC_CACHE -> spanSink.completedSpans()
            SessionSnapshotType.JVM_CRASH -> currentSessionSpan.endSession(AppTerminationCause.Crash)
        }.map(EmbraceSpanData::toNewPayload)
    }

    private fun retrieveSpanSnapshotData(): List<Span>? = captureDataSafely(logger) {
        spanRepository.getActiveSpans().mapNotNull(PersistableEmbraceSpan::snapshot)
    }
}
