package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch

internal class CompletedSpansWriterTest {

    private companion object {
        private const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"
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

        private val completed = listOf(fullyPopulatedSpan)

        private val twoSpanBudget = 2L * CompletedSpans.ADAPTER.encode(
            CompletedSpans(spans = listOf(span("aaaaaaaaaaaaaaa1").toProto())),
        ).size

        private fun span(id: String, name: String = "emb-network-request") =
            fullyPopulatedSpan.copy(spanId = id, name = name)
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var logger: FakeInternalLogger
    private lateinit var writer: CompletedSpansWriter

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        activePart = partDirectory
        writer = CompletedSpansWriter(lazy { sessionsDir }, { activePart }, logger)
        createPartDir(partDirectory)
    }

    @Test
    fun `the completed spans are written into the session part directory`() {
        assertTrue(write())
        assertTrue(logFile().isFile)
        assertNoInternalErrors()
    }

    @Test
    fun `every span field survives the log`() {
        write()
        assertEquals(listOf(fullyPopulatedSpanProto), readLog())
    }

    @Test
    fun `a span with no populated fields is logged`() {
        assertTrue(write(spans = listOf(Span())))
        assertEquals(listOf(SpanProto()), readLog())
    }

    @Test
    fun `spans logged in one call keep their order`() {
        assertTrue(write(spans = listOf(span("aaaaaaaaaaaaaaa1"), span("aaaaaaaaaaaaaaa2"))))
        assertEquals(listOf("aaaaaaaaaaaaaaa1", "aaaaaaaaaaaaaaa2"), readLog().map { it.span_id })
    }

    @Test
    fun `later writes append to the log rather than replacing it`() {
        assertTrue(write(spans = listOf(span("aaaaaaaaaaaaaaa1"))))
        assertTrue(write(spans = listOf(span("aaaaaaaaaaaaaaa2"), span("aaaaaaaaaaaaaaa3"))))
        assertTrue(write(spans = listOf(span("aaaaaaaaaaaaaaa4"))))

        assertEquals(
            listOf("aaaaaaaaaaaaaaa1", "aaaaaaaaaaaaaaa2", "aaaaaaaaaaaaaaa3", "aaaaaaaaaaaaaaa4"),
            readLog().map { it.span_id },
        )
        assertNoInternalErrors()
    }

    @Test
    fun `an empty list creates a log holding no spans`() {
        assertTrue(write(spans = emptyList()))
        assertTrue(logFile().isFile)
        assertEquals(emptyList<SpanProto>(), readLog())
        assertNoInternalErrors()
    }

    @Test
    fun `an empty list leaves the spans already logged in place`() {
        write()
        assertTrue(writer.write(emptyList()))
        assertEquals(listOf(fullyPopulatedSpanProto), readLog())
        assertNoInternalErrors()
    }

    @Test
    fun `the log is the only file the writer creates`() {
        write()
        write()
        assertEquals(listOf(COMPLETED_SPANS_FILE_NAME), partDir().list()?.toList())
    }

    @Test
    fun `nothing is written when no session part is active`() {
        assertFalse(write(directory = null))
        assertFalse(logFile().exists())
        assertNoInternalErrors()
    }

    @Test
    fun `completed spans are ignored once the session part is no longer active`() {
        write()
        activePart = null
        assertFalse(writer.write(listOf(span("aaaaaaaaaaaaaaa9"))))
        assertEquals(listOf(fullyPopulatedSpanProto), readLog())
        assertNoInternalErrors()
    }

    @Test
    fun `empty session ids are supported`() {
        val directory = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID)
        createPartDir(directory)
        assertTrue(write(directory))
        assertEquals(listOf(fullyPopulatedSpanProto), readLog(directory))
    }

    @Test
    fun `each session part directory gets its own log`() {
        val other = SessionPartDirectory(
            timestamp = TIMESTAMP + 1,
            uuid = "d3721de2-490a-533b-cacd-36423d8b6aab",
            userSessionId = "cccccccccccccccccccccccccccccccc",
            sessionPartId = "dddddddddddddddddddddddddddddddd",
        )
        createPartDir(other)

        assertTrue(write())
        assertTrue(write(other, listOf(span("aaaaaaaaaaaaaaa9"))))
        assertEquals("aaaaaaaaaaaaaaa1", readLog().single().span_id)
        assertEquals("aaaaaaaaaaaaaaa9", readLog(other).single().span_id)
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
        writer = CompletedSpansWriter(lazy { sessionsDir }, { error("boom") }, logger)
        assertFalse(writer.write(completed))
        assertEquals(emptyList<String>(), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `a span that cannot be encoded leaves no log on disk`() {
        assertFalse(write(spans = listOf(Span(attributes = ExplodingList()))))
        assertEquals(emptyList<String>(), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `a failed append leaves the spans already logged readable`() {
        assertTrue(write())
        assertFalse(writer.write(listOf(Span(attributes = ExplodingList()))))
        assertEquals(listOf(fullyPopulatedSpanProto), readLog())
        assertWriteFailureTracked()
    }

    @Test
    fun `concurrent appends log every span exactly once`() {
        val threadCount = 8
        val writesPerThread = 25

        val latch = CountDownLatch(1)
        val threads = (0 until threadCount).map { thread ->
            Thread {
                latch.await()
                repeat(writesPerThread) { attempt ->
                    writer.write(listOf(span("aaaaaaaaaaaa$thread$attempt", name = "span-$thread-$attempt")))
                }
            }
        }
        threads.forEach(Thread::start)
        latch.countDown()
        threads.forEach(Thread::join)

        val expected = (0 until threadCount).flatMap { thread ->
            (0 until writesPerThread).map { attempt -> "span-$thread-$attempt" }
        }
        assertEquals(expected.sorted(), readLog().map { it.name }.sorted())
        assertEquals(listOf(COMPLETED_SPANS_FILE_NAME), partDir().list()?.toList())
        assertNoInternalErrors()
    }

    @Test
    fun `an append that would exceed the maximum log size is dropped`() {
        writer = boundedWriter()
        assertTrue(write(spans = listOf(span("aaaaaaaaaaaaaaa1"))))
        assertTrue(write(spans = listOf(span("aaaaaaaaaaaaaaa2"))))
        assertFalse(write(spans = listOf(span("aaaaaaaaaaaaaaa3"))))
        assertWriteFailureTracked()
    }

    @Test
    fun `the spans already logged survive a dropped append`() {
        writer = boundedWriter()
        write(spans = listOf(span("aaaaaaaaaaaaaaa1")))
        write(spans = listOf(span("aaaaaaaaaaaaaaa2")))
        write(spans = listOf(span("aaaaaaaaaaaaaaa3")))
        assertEquals(
            listOf("aaaaaaaaaaaaaaa1", "aaaaaaaaaaaaaaa2"),
            readLog().map(SpanProto::span_id),
        )
    }

    @Test
    fun `an append landing exactly on the maximum log size is written`() {
        writer = boundedWriter()
        assertTrue(write(spans = listOf(span("aaaaaaaaaaaaaaa1"))))
        assertTrue(write(spans = listOf(span("aaaaaaaaaaaaaaa2"))))
        assertEquals(twoSpanBudget, logFile().length())
        assertNoInternalErrors()
    }

    @Test
    fun `a full log is reported once however many appends are dropped`() {
        writer = boundedWriter()
        write(spans = listOf(span("aaaaaaaaaaaaaaa1")))
        write(spans = listOf(span("aaaaaaaaaaaaaaa2")))

        repeat(5) { assertFalse(write(spans = listOf(span("aaaaaaaaaaaaaaa3")))) }
        assertWriteFailureTracked()
    }

    @Test
    fun `an empty append is a no-op once the log is full`() {
        writer = boundedWriter()
        write(spans = listOf(span("aaaaaaaaaaaaaaa1")))
        write(spans = listOf(span("aaaaaaaaaaaaaaa2")))

        assertTrue(write(spans = emptyList()))
        assertEquals(twoSpanBudget, logFile().length())
        assertNoInternalErrors()
    }

    private fun boundedWriter(): CompletedSpansWriter =
        CompletedSpansWriter(lazy { sessionsDir }, { activePart }, logger, twoSpanBudget)

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File =
        File(sessionsDir, directory.dirName)

    private fun logFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), COMPLETED_SPANS_FILE_NAME)

    private fun readLog(directory: SessionPartDirectory = partDirectory): List<SpanProto> =
        logFile(directory).source().buffer().use(::readCompletedSpans)

    private fun write(
        directory: SessionPartDirectory? = partDirectory,
        spans: List<Span> = completed,
    ): Boolean {
        activePart = directory
        return writer.write(spans)
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertWriteFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("CompletedSpansWriteFail", logger.internalErrorMessages.single().msg)
    }

    /**
     * A list that fails when it is read, standing in for any input that blows up while the log
     * records are being built.
     */
    private class ExplodingList : List<Attribute> by emptyList() {
        override fun iterator(): Iterator<Attribute> = error("boom")
    }
}
