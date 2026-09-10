package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpans
import io.embrace.android.embracesdk.internal.session.persistence.EnvelopeMetadataProto
import io.embrace.android.embracesdk.internal.session.persistence.SessionManifest
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartFile
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartSource
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartSpan
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshots
import io.embrace.android.embracesdk.internal.session.persistence.buildCompletedSpans
import io.embrace.android.embracesdk.internal.session.persistence.buildEnvelopeMetadata
import io.embrace.android.embracesdk.internal.session.persistence.buildSessionManifest
import io.embrace.android.embracesdk.internal.session.persistence.buildSessionPartSpan
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
        SessionPartFile.MANIFEST to SessionManifest.ADAPTER.encode(
            buildSessionManifest(
                resource = session.resource,
                directory = session.directory,
                envelopeVersion = session.envelopeVersion,
                envelopeType = session.envelopeType,
                sharedLibSymbolMapping = null,
            ),
        ),
        SessionPartFile.METADATA to EnvelopeMetadataProto.ADAPTER.encode(
            buildEnvelopeMetadata(session.metadata, session.resource),
        ),
        SessionPartFile.SESSION_SPAN to SessionPartSpan.ADAPTER.encode(
            buildSessionPartSpan(session.sessionSpan),
        ),
        SessionPartFile.SPAN_SNAPSHOTS to SpanSnapshots.ADAPTER.encode(
            buildSpanSnapshots(session.spanSnapshots),
        ),
        SessionPartFile.COMPLETED_SPANS to CompletedSpans.ADAPTER.encode(
            buildCompletedSpans(session.completedSpans),
        ),
    )

    fun newPartSource(): SessionPartSource = InMemorySessionPartSource(partFiles)
}
