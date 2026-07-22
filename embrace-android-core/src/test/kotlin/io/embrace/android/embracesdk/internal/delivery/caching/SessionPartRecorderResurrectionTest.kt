package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.delivery.storage.session.Manifest
import io.embrace.android.embracesdk.internal.delivery.storage.session.PersistedSession
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionPartStoreImpl
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionPartWriter
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Verifies that a session-part directory left on disk by a crashed process is recovered on the next
 * launch, with its still-open span snapshots converted into failed spans.
 */
class SessionPartRecorderResurrectionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `leftover session part directory is resurrected with open snapshots failed`() {
        val store = SessionPartStoreImpl(tmp.root)

        // Simulate a session part left behind by a crashed process (no complete marker).
        val directory = PersistedSession(1000L, "uuid", "user-1", "part-1").directory(tmp.root).apply { create() }
        val writer = SessionPartWriter(directory)
        writer.writeManifest(Manifest(EnvelopeResource(appVersion = "1.0.0"), "0.1.0", "spans"))
        writer.writeMetadata(SessionMetadata(EnvelopeMetadata(userId = "user-1")))
        writer.appendCompletedSpans(listOf(span("completed", "network-request")))
        writer.writeSnapshots(listOf(span("open", "in-flight")))
        writer.writeSessionSpan(
            span(
                id = "session",
                name = "emb-session",
                attrs = mapOf(
                    "emb.type" to "ux.session",
                    "emb.heartbeat_time_unix_nano" to "2000000000",
                ),
            ),
        )

        val recorder = SessionPartRecorder(
            store = store,
            spanRepository = SpanRepository(),
            spanSink = SpanSinkImpl(),
            resourceProvider = { EnvelopeResource() },
            metadataProvider = { EnvelopeMetadata() },
            sessionIdsProvider = { SessionIdsSnapshot("", "") },
            clock = FakeClock(),
            uuidProvider = { "uuid" },
        )

        val leftovers = store.incompleteDirectories()
        assertEquals(1, leftovers.size)

        val resurrected = recorder.resurrect(leftovers)
        assertEquals(1, resurrected.size)

        val envelope = resurrected.single()
        // the open snapshot has been converted into a failed span and moved into `spans`
        assertNull(envelope.data.spanSnapshots?.takeIf { it.isNotEmpty() })
        val failed = envelope.data.spans?.single { it.spanId == "open" }
        assertEquals(Span.Status.ERROR, failed?.status)
        assertTrue(envelope.data.spans?.any { it.spanId == "completed" } == true)
        assertTrue(envelope.data.spans?.any { it.name == "emb-session" } == true)

        // the directory is marked delivered so it is not resurrected again
        assertTrue(store.incompleteDirectories().isEmpty())
    }

    private fun span(id: String, name: String, attrs: Map<String, String> = mapOf("emb.type" to "perf")) = Span(
        spanId = id,
        name = name,
        startTimeNanos = 1_000L,
        status = Span.Status.UNSET,
        attributes = attrs.map { Attribute(it.key, it.value) },
    )
}
