package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpans
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadata
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartFile
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartSource
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshots
import io.embrace.android.embracesdk.internal.session.persistence.buildCompletedSpans
import io.embrace.android.embracesdk.internal.session.persistence.buildSessionMetadata
import io.embrace.android.embracesdk.internal.session.persistence.buildSpanSnapshots
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * One short session's worth of telemetry, in the bytes each persistence layer would have stored.
 */
internal class StoredSessionFixture(session: SimpleSessionFixture = SimpleSessionFixture()) {

    val singleFileBytes: ByteArray = ByteArrayOutputStream().apply {
        EmbraceSerializer().toJson(session.envelope, Envelope.sessionEnvelopeSerializer, GZIPOutputStream(this))
    }.toByteArray()

    val expectedSpanCount: Int = session.completedSpans.size + session.spanSnapshots.size + 1

    private val partFiles: Map<SessionPartFile, ByteArray> = mapOf(
        SessionPartFile.METADATA to SessionMetadata.ADAPTER.encode(
            buildSessionMetadata(
                metadata = session.metadata,
                resource = session.resource,
                directory = session.directory,
                envelopeVersion = session.envelopeVersion,
                envelopeType = session.envelopeType,
                sharedLibSymbolMapping = null,
            ),
        ),
        SessionPartFile.SPAN_SNAPSHOTS to SpanSnapshots.ADAPTER.encode(
            buildSpanSnapshots(session.spanSnapshots),
        ),
        // the session span is logged as a completed span once the session ends
        SessionPartFile.COMPLETED_SPANS to CompletedSpans.ADAPTER.encode(
            buildCompletedSpans(session.completedSpans + session.sessionSpan),
        ),
    )

    fun newPartSource(): SessionPartSource = InMemorySessionPartSource(partFiles)
}
