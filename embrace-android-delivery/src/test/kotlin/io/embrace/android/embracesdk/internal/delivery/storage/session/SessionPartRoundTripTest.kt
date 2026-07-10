package io.embrace.android.embracesdk.internal.delivery.storage.session

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Round-trip test: write the constituent files of a session part, then reconstitute the directory
 * and confirm the resulting envelope carries the same spans/resource/metadata.
 */
class SessionPartRoundTripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val resource = EnvelopeResource(
        appVersion = "1.2.3",
        appFramework = AppFramework.NATIVE,
        sdkVersion = "7.0.0",
        deviceModel = "Pixel 9",
        numCores = 8,
        extras = mapOf("k" to "v"),
    )
    private val metadata = EnvelopeMetadata(userId = "user-1", locale = "en_US", personas = setOf("power_user"))

    private fun span(id: String, name: String, attrs: Map<String, String> = mapOf("emb.type" to "perf")) = Span(
        traceId = "trace-$id",
        spanId = id,
        name = name,
        startTimeNanos = 1_000L,
        endTimeNanos = 2_000L,
        status = Span.Status.OK,
        attributes = attrs.map { Attribute(it.key, it.value) },
        events = listOf(SpanEvent(name = "evt", timestampNanos = 1_500L)),
    )

    @Test
    fun `write and reconstitute a session part`() {
        val descriptor = PersistedSession(
            timestamp = 1234L,
            uuid = "abcd",
            userSessionId = "user-session",
            sessionPartId = "part-1",
        )
        val directory = descriptor.directory(tmp.root).apply { create() }
        val writer = SessionPartWriter(directory)

        writer.writeManifest(Manifest(resource, version = "0.1.0", type = "spans", sharedLibSymbolMapping = mapOf("a" to "b")))
        writer.writeMetadata(SessionMetadata(metadata))

        val completedA = span("s1", "network-request")
        val completedB = span("s2", "db-query")
        writer.appendCompletedSpans(listOf(completedA))
        writer.appendCompletedSpans(listOf(completedB)) // second append must not clobber the first

        val snapshot = span("s3", "in-flight")
        writer.writeSnapshots(listOf(snapshot))

        val sessionSpan = span("sess", "session", mapOf("emb.session_part_id" to "part-1"))
        writer.writeSessionSpan(sessionSpan)

        // assert the layout exists on disk
        assertTrue(directory.manifestFile.exists())
        assertTrue(directory.metadataFile.exists())
        assertTrue(directory.completedSpansFile.exists())
        assertTrue(directory.snapshotsFile.exists())
        assertTrue(directory.sessionSpanFile.exists())

        val envelope = checkNotNull(SessionPartReader().reconstitute(directory))

        assertEquals("0.1.0", envelope.version)
        assertEquals("spans", envelope.type)
        assertEquals(resource, envelope.resource)
        assertEquals(metadata, envelope.metadata)
        assertEquals(mapOf("a" to "b"), envelope.data.sharedLibSymbolMapping)

        // completed spans + session span end up in `spans`; snapshot stays in `spanSnapshots`
        assertEquals(setOf(completedA, completedB, sessionSpan), envelope.data.spans?.toSet())
        assertEquals(listOf(snapshot), envelope.data.spanSnapshots)
    }

    @Test
    fun `directory name round-trips`() {
        val descriptor = PersistedSession(9L, "uu", "usid", "spid")
        assertEquals(descriptor, PersistedSession.fromDirName(descriptor.dirName))
    }
}
