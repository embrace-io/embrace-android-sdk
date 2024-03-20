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
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.session.captureDataSafely
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.spans.PersistableEmbraceSpan

internal class SessionPayloadSourceImpl(
    private val internalErrorService: InternalErrorService,
    private val nativeThreadSamplerService: NativeThreadSamplerService?,
    private val spanSink: SpanSink,
    private val currentSessionSpan: CurrentSessionSpan,
    private val spanRepository: SpanRepository
) : SessionPayloadSource {

    override fun getSessionPayload(endType: SessionSnapshotType): SessionPayload {
        return SessionPayload(
            spans = retrieveSpanData(endType),
            spanSnapshots = retrieveSpanSnapshotData(),
            internalError = captureDataSafely { internalErrorService.currentExceptionError?.toNewPayload() },
            sharedLibSymbolMapping = captureDataSafely { nativeThreadSamplerService?.getNativeSymbols() }
        )
    }

    private fun retrieveSpanData(endType: SessionSnapshotType): List<Span>? = captureDataSafely {
        when (endType) {
            SessionSnapshotType.NORMAL_END -> currentSessionSpan.endSession(null)
            SessionSnapshotType.PERIODIC_CACHE -> spanSink.completedSpans()
            SessionSnapshotType.JVM_CRASH -> currentSessionSpan.endSession(AppTerminationCause.Crash)
        }.map(EmbraceSpanData::toNewPayload)
    }

    private fun retrieveSpanSnapshotData(): List<Span>? = captureDataSafely {
        spanRepository.getActiveSpans().mapNotNull(PersistableEmbraceSpan::snapshot)
    }
}
