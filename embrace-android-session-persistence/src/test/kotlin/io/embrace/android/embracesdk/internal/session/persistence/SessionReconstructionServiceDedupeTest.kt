package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionReconstructionServiceDedupeTest {

    private companion object {
        private const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"
        private const val ENVELOPE_VERSION = "0.1.0"
        private const val ENVELOPE_TYPE = "spans"
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        private const val EARLY_END = 1726739283900000000L
        private const val LATE_END = 1726739283950000000L

        private val partDirectory = SessionPartDirectory(
            timestamp = TIMESTAMP,
            uuid = UUID,
            userSessionId = USER_SESSION_ID,
            sessionPartId = SESSION_PART_ID,
        )

        private val earlySpanProto = inFlightSpanProto.copy(
            end_time_unix_nano = EARLY_END,
            status = SpanProto.Status.OK,
        )
        private val earlySpan = inFlightSpan.copy(endTimeNanos = EARLY_END, status = Span.Status.OK)

        private val lateSpanProto = earlySpanProto.copy(end_time_unix_nano = LATE_END)
        private val lateSpan = earlySpan.copy(endTimeNanos = LATE_END)

        private val otherSpanProto = earlySpanProto.copy(span_id = "aaaaaaaaaaaaaaa5")
        private val otherSpan = earlySpan.copy(spanId = "aaaaaaaaaaaaaaa5")

        private val otherSnapshot = inFlightSpan.copy(spanId = "aaaaaaaaaaaaaaa7")

        private val anonymousSpanProto = inFlightSpanProto.copy(span_id = "")
        private val anonymousSpan = inFlightSpan.copy(spanId = null)
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var logger: FakeInternalLogger
    private lateinit var manifestWriter: SessionManifestWriter
    private lateinit var metadataWriter: SessionMetadataWriter
    private lateinit var sessionSpanWriter: SessionSpanWriter
    private lateinit var snapshotsWriter: SpanSnapshotsWriter
    private lateinit var service: SessionReconstructionService

    @Volatile
    private var sessionSpan: Span = fullyPopulatedSpan

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        sessionSpan = fullyPopulatedSpan
        activePart = partDirectory
        manifestWriter = SessionManifestWriter(lazy { sessionsDir }, logger)
        metadataWriter = SessionMetadataWriter(
            lazy { sessionsDir },
            { activePart },
            { fullyPopulatedMetadata },
            { fullyPopulatedResource },
            logger,
        )
        sessionSpanWriter = SessionSpanWriter(lazy { sessionsDir }, { activePart }, logger)
        snapshotsWriter = SpanSnapshotsWriter(lazy { sessionsDir }, { activePart }, logger)
        service = SessionReconstructionService(lazy { sessionsDir }, logger)
        createPartDir(partDirectory)
    }

    @Test
    fun `a completed span supersedes a snapshot of the same span`() {
        write(spans = listOf(earlySpanProto), snapshots = listOf(inFlightSpan))

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(earlySpan, fullyPopulatedSpan), payload.spans)
        assertNull(payload.spanSnapshots)
        assertDuplicatesTracked()
    }

    @Test
    fun `a completed span wins over a snapshot that ended later`() {
        write(spans = listOf(earlySpanProto), snapshots = listOf(lateSpan))

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(earlySpan, fullyPopulatedSpan), payload.spans)
        assertNull(payload.spanSnapshots)
        assertDuplicatesTracked()
    }

    @Test
    fun `duplicates in the completed spans log keep the one that ended last`() {
        write(spans = listOf(lateSpanProto, earlySpanProto))

        assertEquals(listOf(lateSpan, fullyPopulatedSpan), service.reconstruct(partDirectory)?.data?.spans)
        assertDuplicatesTracked()
    }

    @Test
    fun `duplicate snapshots keep the one that ended last`() {
        write(snapshots = listOf(earlySpan, lateSpan))

        assertEquals(listOf(lateSpan), service.reconstruct(partDirectory)?.data?.spanSnapshots)
        assertDuplicatesTracked()
    }

    @Test
    fun `dedupe preserves the order of the remaining spans`() {
        write(
            spans = listOf(earlySpanProto, otherSpanProto, lateSpanProto),
            snapshots = listOf(inFlightSpan, otherSnapshot),
        )

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(otherSpan, lateSpan, fullyPopulatedSpan), payload.spans)
        assertEquals(listOf(otherSnapshot), payload.spanSnapshots)
        assertDuplicatesTracked()
    }

    @Test
    fun `one internal error is logged however many duplicates are removed`() {
        write(
            spans = listOf(earlySpanProto, earlySpanProto, otherSpanProto, otherSpanProto),
            snapshots = listOf(inFlightSpan, otherSnapshot, otherSnapshot),
        )

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(earlySpan, otherSpan, fullyPopulatedSpan), payload.spans)
        assertEquals(listOf(otherSnapshot), payload.spanSnapshots)
        assertDuplicatesTracked()
    }

    @Test
    fun `spans with no span id are not treated as duplicates`() {
        write(spans = listOf(anonymousSpanProto, anonymousSpanProto))

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(anonymousSpan, anonymousSpan, fullyPopulatedSpan), payload.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `a payload with no duplicates logs no internal error`() {
        write(spans = listOf(earlySpanProto, otherSpanProto), snapshots = listOf(otherSnapshot))

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(earlySpan, otherSpan, fullyPopulatedSpan), payload.spans)
        assertEquals(listOf(otherSnapshot), payload.spanSnapshots)
        assertNoInternalErrors()
    }

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun write(spans: List<SpanProto> = emptyList(), snapshots: List<Span> = emptyList()) {
        assertTrue(manifestWriter.write(partDirectory, fullyPopulatedResource, ENVELOPE_VERSION, ENVELOPE_TYPE))
        assertTrue(metadataWriter.write())
        assertTrue(sessionSpanWriter.write(sessionSpan))
        File(File(sessionsDir, partDirectory.dirName), COMPLETED_SPANS_FILE_NAME)
            .writeBytes(completedSpansLog(spans))
        assertTrue(snapshotsWriter.write(snapshots))
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertDuplicatesTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("DuplicateSpanIds", logger.internalErrorMessages.single().msg)
    }
}
