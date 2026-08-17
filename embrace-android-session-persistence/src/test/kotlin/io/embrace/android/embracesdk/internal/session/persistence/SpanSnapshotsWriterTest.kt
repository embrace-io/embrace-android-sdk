package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch

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
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

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
        writer = SpanSnapshotsWriter(lazy { sessionsDir }, { activePart }, logger)
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
    fun `a file occupying the session part path is reported and left untouched`() {
        val occupied = SessionPartDirectory(timestamp = TIMESTAMP + 3, uuid = UUID)
        val occupyingFile = partDir(occupied).apply { writeText("not a directory") }
        assertFalse(write(occupied))
        assertWriteFailureTracked()
        assertEquals("not a directory", occupyingFile.readText())
    }

    @Test
    fun `a failing session part source is reported and does not throw`() {
        writer = SpanSnapshotsWriter(lazy { sessionsDir }, { error("boom") }, logger)
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
    fun `concurrent span snapshot writes leave one valid file`() {
        val threadCount = 8
        val writesPerThread = 25
        write()

        val spanIds = (0 until threadCount).map { "aaaaaaaaaaaaaaa$it" }
        val latch = CountDownLatch(1)
        val threads = spanIds.map { spanId ->
            Thread {
                latch.await()
                repeat(writesPerThread) {
                    writer.write(listOf(fullyPopulatedSpan.copy(spanId = spanId)))
                }
            }
        }
        threads.forEach(Thread::start)
        latch.countDown()
        threads.forEach(Thread::join)

        assertTrue(readSnapshots().spans.single().span_id in spanIds)
        assertEquals(listOf(SPAN_SNAPSHOTS_FILE_NAME), partDir().list()?.toList())
        assertNoInternalErrors()
    }

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
