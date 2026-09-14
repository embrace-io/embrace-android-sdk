package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SpanSnapshotsWriterTest {

    private companion object {
        private const val SPAN_SNAPSHOTS_FILE_NAME = "span_snapshots.pb"
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        private val partDirectory = SessionPartDirectory(
            timestamp = TIMESTAMP,
            uuid = UUID,
            userSessionId = USER_SESSION_ID,
            sessionPartId = SESSION_PART_ID,
        )

        private val snapshots = listOf(fullyPopulatedSpan, inFlightSpan)

        private fun snapshot(spanId: String, name: String) =
            inFlightSpan.copy(spanId = spanId, name = name)
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private val first = snapshot("aaaaaaaaaaaaaaa1", "emb-first")
    private val second = snapshot("aaaaaaaaaaaaaaa2", "emb-second")
    private val third = snapshot("aaaaaaaaaaaaaaa3", "emb-third")

    private lateinit var sessionsDir: File
    private lateinit var logger: FakeInternalLogger
    private lateinit var writer: SpanSnapshotsWriter

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        activePart = partDirectory
        writer = SpanSnapshotsWriter(target { activePart }, logger)
        createPartDir(partDirectory)
    }

    @Test
    fun `the span snapshots are written into the session part directory`() {
        assertTrue(write())
        assertTrue(snapshotsFile().isFile)
        assertNoInternalErrors()
    }

    @Test
    fun `every span field is persisted`() {
        write()

        val decoded = readSnapshots()
        assertEquals(fullyPopulatedSpanSnapshotsProto, decoded)
        assertEquals(fullyPopulatedSpanProto, decoded.spans.first())
    }

    @Test
    fun `span order is preserved`() {
        val spans = listOf("first", "second", "third").mapIndexed { index, name ->
            fullyPopulatedSpan.copy(spanId = "aaaaaaaaaaaaaaa$index", name = name)
        }
        assertTrue(write(span = spans))
        assertEquals(listOf("first", "second", "third"), readSnapshots().spans.map { it.name })
    }

    @Test
    fun `an in-flight span keeps its null end time`() {
        write()

        val decoded = readSnapshots().spans
        assertEquals(fullyPopulatedSpan.endTimeNanos, decoded.first().end_time_unix_nano)
        assertNull(decoded.last().end_time_unix_nano)
    }

    @Test
    fun `the format version is persisted even when nothing else is populated`() {
        assertTrue(write(span = listOf(Span())))
        val decoded = readSnapshots()
        assertEquals(FORMAT_VERSION, decoded.format_version)
        assertEquals(listOf(SpanProto()), decoded.spans)
    }

    @Test
    fun `an empty list of spans is still written`() {
        assertTrue(write(span = emptyList()))
        assertEquals(SpanSnapshots(format_version = FORMAT_VERSION), readSnapshots())
        assertNoInternalErrors()
    }

    @Test
    fun `an empty list replaces spans that were previously in flight`() {
        write()
        assertTrue(writer.write(emptyList()))
        assertEquals(emptyList<SpanProto>(), readSnapshots().spans)
        assertNoInternalErrors()
    }

    @Test
    fun `the span snapshots are overwritten as spans change`() {
        write()
        val updated = listOf(
            inFlightSpan.copy(
                attributes = listOf(Attribute(key = "http.response.status_code", data = "200")),
            ),
        )
        assertTrue(writer.write(updated))
        assertEquals(updated.map(Span::toProto), readSnapshots().spans)
        assertEquals(listOf(SPAN_SNAPSHOTS_FILE_NAME), partDir().list()?.toList())
        assertNoInternalErrors()
    }

    @Test
    fun `nothing is written when no session part is active`() {
        assertFalse(write(directory = null))
        assertFalse(snapshotsFile().exists())
        assertNoInternalErrors()
    }

    @Test
    fun `span changes are ignored once the session part is no longer active`() {
        write()
        activePart = null
        assertFalse(writer.write(listOf(fullyPopulatedSpan.copy(name = "other"))))
        assertEquals("emb-session", readSnapshots().spans.first().name)
        assertNoInternalErrors()
    }

    @Test
    fun `empty session ids are supported`() {
        val directory = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID)
        createPartDir(directory)
        assertTrue(write(directory))
        assertEquals(fullyPopulatedSpanSnapshotsProto, readSnapshots(directory))
    }

    @Test
    fun `each session part directory gets its own span snapshots`() {
        val other = SessionPartDirectory(
            timestamp = TIMESTAMP + 1,
            uuid = "d3721de2-490a-533b-cacd-36423d8b6aab",
            userSessionId = "cccccccccccccccccccccccccccccccc",
            sessionPartId = "dddddddddddddddddddddddddddddddd",
        )
        createPartDir(other)

        assertTrue(write())
        assertTrue(write(other, listOf(fullyPopulatedSpan.copy(spanId = "aaaaaaaaaaaaaaa9"))))
        assertEquals("aaaaaaaaaaaaaaa1", readSnapshots().spans.first().span_id)
        assertEquals("aaaaaaaaaaaaaaa9", readSnapshots(other).spans.single().span_id)
        assertNoInternalErrors()
    }

    @Test
    fun `no temporary files are left behind`() {
        write()
        writer.write(snapshots)
        assertEquals(listOf(SPAN_SNAPSHOTS_FILE_NAME), partDir().list()?.toList())
    }

    @Test
    fun `a stale temporary file does not prevent a write`() {
        File(partDir(), "${SPAN_SNAPSHOTS_FILE_NAME}1234.tmp").writeText("torn write")
        assertTrue(write())
        assertEquals(fullyPopulatedSpanSnapshotsProto, readSnapshots())
        assertNoInternalErrors()
    }

    @Test
    fun `missing session part directory is reported and does not throw`() {
        val absent = SessionPartDirectory(timestamp = TIMESTAMP + 2, uuid = UUID)
        assertFalse(write(absent))
        assertFalse(partDir(absent).exists())
        assertWriteFailureTracked()
    }

    @Test
    fun `a session part with no directory is given up on and reported once`() {
        val absent = SessionPartDirectory(timestamp = TIMESTAMP + 2, uuid = UUID)
        repeat(20) {
            assertFalse(write(absent))
        }
        assertWriteFailureTracked()
    }

    @Test
    fun `a file occupying the session part path is reported and left untouched`() {
        val occupied = SessionPartDirectory(timestamp = TIMESTAMP + 3, uuid = UUID)
        val occupyingFile = partDir(occupied).apply { writeText("not a directory") }
        assertFalse(write(occupied))
        assertWriteFailureTracked()
        assertEquals("not a directory", occupyingFile.readText())
    }

    @Test
    fun `a failing session part source is reported and does not throw`() {
        writer = SpanSnapshotsWriter(target { error("boom") }, logger)
        assertFalse(writer.write(snapshots))
        assertEquals(emptyList<String>(), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `failure building the span snapshots leaves no files on disk`() {
        assertFalse(write(span = listOf(Span(attributes = ExplodingList()))))
        assertEquals(emptyList<String>(), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `a failed overwrite leaves the previous span snapshots intact`() {
        assertTrue(write())
        assertFalse(writer.write(listOf(Span(attributes = ExplodingList()))))
        assertEquals(fullyPopulatedSpanSnapshotsProto, readSnapshots())
        assertEquals(listOf(SPAN_SNAPSHOTS_FILE_NAME), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `a span too large for the log is dropped and the spans before it are kept`() {
        val oversized = paddedSpan(paddedSpanId(0), MAX_PART_FILE_BYTES.toInt() + 1)
        assertTrue(write(span = listOf(first, oversized, second)))
        assertEquals(protos(first), readSnapshots().spans)
        assertEquals(listOf(SPAN_SNAPSHOTS_FILE_NAME), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `a span larger than one record is dropped and the spans round it are kept`() {
        val oversized = paddedSpan(paddedSpanId(0), MAX_RECORD_BYTES.toInt())
        assertTrue(write(span = listOf(first, oversized, second)))
        assertEquals(protos(first, second), readSnapshots().spans)
        assertWriteFailureTracked()
    }

    @Test
    fun `an append adds to the file rather than replacing it`() {
        assertTrue(write(span = listOf(first)))
        assertTrue(append(listOf(second)))
        assertEquals(protos(first, second), loggedRecords())
        assertEquals(protos(first, second), latestSnapshots())
        assertNoInternalErrors()
    }

    @Test
    fun `an append supersedes the record already recorded for a span`() {
        assertTrue(write(span = listOf(first, second)))
        val updated = first.copy(name = "renamed")
        assertTrue(append(listOf(updated)))

        assertEquals(protos(first, second, updated), loggedRecords())
        assertEquals(protos(updated, second), latestSnapshots())
        assertNoInternalErrors()
    }

    @Test
    fun `appending nothing leaves the file untouched`() {
        assertTrue(write(span = listOf(first)))
        assertTrue(writer.append(emptyList()) { error("live spans read for an empty append") })
        assertEquals(protos(first), loggedRecords())
        assertNoInternalErrors()
    }

    @Test
    fun `an append with no records yet rolls up the live spans`() {
        assertTrue(append(listOf(second), live = listOf(first, second)))
        assertEquals(protos(first, second), loggedRecords())
        assertEquals(FORMAT_VERSION, readSnapshots().format_version)
        assertNoInternalErrors()
    }

    @Test
    fun `the file is rolled up once it would hold more snapshots than the limit allows`() {
        writer = writerWith(maxRecords = 3)
        assertTrue(write(span = listOf(first, second)))
        assertTrue(append(listOf(third), live = listOf(first, second, third)))
        assertEquals(protos(first, second, third), loggedRecords())

        val updated = first.copy(name = "renamed")
        assertTrue(append(listOf(updated), live = listOf(updated, second, third)))
        assertEquals(protos(updated, second, third), loggedRecords())
        assertEquals(protos(updated, second, third), latestSnapshots())
        assertNoInternalErrors()
    }

    @Test
    fun `the file is appended to again after a rollup`() {
        writer = writerWith(maxRecords = 4)
        assertTrue(write(span = listOf(first, second)))
        val renamedFirst = first.copy(name = "renamed first")
        val renamedSecond = second.copy(name = "renamed second")
        assertTrue(append(listOf(renamedFirst)))
        assertTrue(append(listOf(renamedSecond)))
        assertEquals(protos(first, second, renamedFirst, renamedSecond), loggedRecords())

        val rolledUp = listOf(first.copy(name = "rolled up"), renamedSecond)
        assertTrue(append(listOf(rolledUp.first()), live = rolledUp))
        assertEquals(rolledUp.map(Span::toProto), loggedRecords())

        val appended = renamedSecond.copy(name = "appended")
        assertTrue(append(listOf(appended)))
        assertEquals((rolledUp + appended).map(Span::toProto), loggedRecords())
        assertEquals(protos(rolledUp.first(), appended), latestSnapshots())
        assertNoInternalErrors()
    }

    @Test
    fun `the live spans are only read when the file is rolled up`() {
        writer = writerWith(maxRecords = 3)
        assertTrue(write(span = listOf(first, second)))

        var reads = 0
        val live = {
            reads++
            listOf(first, second)
        }
        assertTrue(writer.append(listOf(first), live))
        assertEquals(0, reads)
        assertTrue(writer.append(listOf(first), live))
        assertEquals(1, reads)
    }

    @Test
    fun `the file stays within the record limit as spans keep changing`() {
        writer = writerWith(maxRecords = 4)
        assertTrue(write(span = listOf(first, second)))

        var live = listOf(first, second)
        repeat(50) { index ->
            val changed = live[index % 2].copy(name = "change $index")
            live = live.map { if (it.spanId == changed.spanId) changed else it }
            assertTrue(append(listOf(changed), live = live))
            assertTrue(loggedRecords().size <= 4)
        }
        assertEquals(live.map(Span::toProto), latestSnapshots())
        assertNoInternalErrors()
    }

    @Test
    fun `an append that would overflow the part file rolls the file up`() {
        val rolledUp = listOf(first, second)
        val updated = first.copy(name = "renamed")
        writer = writerWith(maxBytes = rollupSize(rolledUp) + appendSize(listOf(updated)) - 1)

        assertTrue(write(span = rolledUp))
        assertTrue(append(listOf(updated), live = listOf(updated, second)))
        assertEquals(protos(updated, second), loggedRecords())
        assertNoInternalErrors()
    }

    @Test
    fun `an append rolls up into the session part that is now active`() {
        val other = SessionPartDirectory(timestamp = TIMESTAMP + 4, uuid = UUID)
        createPartDir(other)
        assertTrue(write(span = listOf(first)))

        assertTrue(append(listOf(second), live = listOf(second), directory = other))
        assertEquals(protos(first), loggedRecords())
        assertEquals(protos(second), loggedRecords(other))
        assertNoInternalErrors()
    }

    @Test
    fun `nothing is appended once the session part is no longer active`() {
        assertTrue(write(span = listOf(first)))
        assertFalse(append(listOf(second), directory = null))
        assertEquals(protos(first), loggedRecords())
        assertNoInternalErrors()
    }

    @Test
    fun `the next append rolls up once the log has been closed`() {
        assertTrue(write(span = listOf(first)))
        writer.close()
        assertTrue(append(listOf(second), live = listOf(first, second)))
        assertEquals(protos(first, second), loggedRecords())
        assertNoInternalErrors()
    }

    @Test
    fun `an oversized span is dropped from an append and reported once`() {
        assertTrue(write(span = listOf(first)))
        val oversized = paddedSpan(paddedSpanId(0), MAX_RECORD_BYTES.toInt())
        repeat(3) {
            assertFalse(append(listOf(oversized)))
        }
        assertEquals(protos(first), loggedRecords())
        assertWriteFailureTracked()
    }

    @Test
    fun `an append to a session part directory that has gone is reported`() {
        assertTrue(write(span = listOf(first)))
        partDir().deleteRecursively()
        assertFalse(append(listOf(second)))
        assertWriteFailureTracked()
    }

    private fun target(source: () -> SessionPartDirectory?): SessionPartWriteTarget =
        SessionPartWriteTarget(lazy { sessionsDir }, source)

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File =
        File(sessionsDir, directory.dirName)

    private fun snapshotsFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), SPAN_SNAPSHOTS_FILE_NAME)

    private fun readSnapshots(directory: SessionPartDirectory = partDirectory): SpanSnapshots =
        snapshotsFile(directory).inputStream().use(SpanSnapshots.ADAPTER::decode)

    private fun write(
        directory: SessionPartDirectory? = partDirectory,
        span: List<Span> = snapshots,
    ): Boolean {
        activePart = directory
        return writer.write(span)
    }

    private fun writerWith(
        maxBytes: Long = MAX_PART_FILE_BYTES,
        maxRecordBytes: Long = MAX_RECORD_BYTES,
        maxRecords: Int = MAX_PERSISTED_SPANS,
    ): SpanSnapshotsWriter =
        SpanSnapshotsWriter(target { activePart }, logger, maxBytes, maxRecordBytes, maxRecords)

    private fun append(
        spans: List<Span>,
        live: List<Span> = spans,
        directory: SessionPartDirectory? = partDirectory,
    ): Boolean {
        activePart = directory
        return writer.append(spans) { live }
    }

    /** Every record the log holds, superseded ones included, in the order they were written. */
    private fun loggedRecords(directory: SessionPartDirectory = partDirectory): List<SpanProto> =
        readSnapshots(directory).spans

    /** The latest state of each span the log holds, as reconstruction reads it back. */
    private fun latestSnapshots(directory: SessionPartDirectory = partDirectory): List<SpanProto> =
        snapshotsFile(directory).source().buffer().use { readSpanSnapshots(it).spans }

    private fun protos(vararg spans: Span): List<SpanProto> = spans.map(Span::toProto)

    private fun rollupSize(spans: List<Span>): Long = SpanSnapshots.ADAPTER.encodedSize(
        SpanSnapshots(format_version = FORMAT_VERSION, spans = spans.map(Span::toProto)),
    ).toLong()

    private fun appendSize(spans: List<Span>): Long =
        SpanSnapshots.ADAPTER.encodedSize(SpanSnapshots(spans = spans.map(Span::toProto))).toLong()

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertWriteFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SpanSnapshotsWriteFail", logger.internalErrorMessages.single().msg)
    }

    /**
     * A list that fails when it is read, standing in for any input that blows up while the span
     * snapshots are being built.
     */
    private class ExplodingList : List<Attribute> by emptyList() {
        override fun iterator(): Iterator<Attribute> = error("boom")
    }
}
