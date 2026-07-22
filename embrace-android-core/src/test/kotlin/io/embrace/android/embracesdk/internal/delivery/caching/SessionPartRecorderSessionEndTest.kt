package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.internal.delivery.storage.session.Manifest
import io.embrace.android.embracesdk.internal.delivery.storage.session.PersistedSession
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionPartDirectory
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionPartStore
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.toEmbraceSpanData
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Verifies that [SessionPartRecorder.onSessionEnd] flushes span changes that arrived after the last
 * [SessionPartRecorder.onSpanUpdate], and is a no-op when nothing has changed since.
 */
class SessionPartRecorderSessionEndTest {

    private lateinit var store: RecordingStore
    private lateinit var spanSink: SpanSinkImpl
    private lateinit var recorder: SessionPartRecorder

    @Before
    fun setUp() {
        store = RecordingStore()
        spanSink = SpanSinkImpl()
        recorder = SessionPartRecorder(
            store = store,
            spanRepository = SpanRepository(),
            spanSink = spanSink,
            resourceProvider = { EnvelopeResource() },
            metadataProvider = { EnvelopeMetadata() },
            sessionIdsProvider = { SessionIdsSnapshot("user-1", "part-1") },
            clock = FakeClock(),
            uuidProvider = { "uuid" },
        )
    }

    @Test
    fun `onSessionEnd flushes spans that changed after the last onSpanUpdate`() {
        recorder.onSpanUpdate()
        assertEquals(1, store.appendCalls)

        // A span completes between the last span-update and the session ending.
        spanSink.storeCompletedSpans(listOf(FakeSpanData(name = "late").toEmbraceSpanData()))
        recorder.onSessionEnd()

        assertEquals(2, store.appendCalls)
        assertEquals(listOf("late"), store.lastCompleted.map(Span::name))
    }

    @Test
    fun `onSessionEnd is a no-op when spans are unchanged since the last invocation`() {
        spanSink.storeCompletedSpans(listOf(FakeSpanData(name = "done").toEmbraceSpanData()))
        recorder.onSpanUpdate()
        assertEquals(1, store.appendCalls)

        recorder.onSessionEnd()
        assertEquals(1, store.appendCalls)
    }

    private class RecordingStore : SessionPartStore {
        private val active = mutableSetOf<String>()
        var appendCalls = 0
        var lastCompleted: List<Span> = emptyList()

        override fun isSessionPartActive(sessionPartId: String): Boolean = sessionPartId in active
        override fun startSessionPart(session: PersistedSession, manifest: Manifest, metadata: SessionMetadata) {
            active.add(session.sessionPartId)
        }

        override fun appendCompletedSpans(sessionPartId: String, spans: List<Span>) {
            appendCalls++
            lastCompleted = spans
        }

        override fun updateSnapshots(sessionPartId: String, snapshots: List<Span>) = Unit
        override fun updateMetadata(sessionPartId: String, metadata: SessionMetadata) = Unit
        override fun onCacheSnapshot(envelope: Envelope<SessionPartPayload>) = Unit
        override fun finalizeSessionPart(envelope: Envelope<SessionPartPayload>): Envelope<SessionPartPayload>? = null
        override fun reconstitute(directory: SessionPartDirectory): Envelope<SessionPartPayload>? = null
        override fun incompleteDirectories(): List<SessionPartDirectory> = emptyList()
        override fun markDelivered(directory: SessionPartDirectory) = Unit
        override fun completedDirectories(): List<SessionPartDirectory> = emptyList()
    }
}
