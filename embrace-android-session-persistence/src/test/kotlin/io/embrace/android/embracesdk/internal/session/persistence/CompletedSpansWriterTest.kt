package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch

internal class CompletedSpansWriterTest {

    private companion object {
        private const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        private const val UNLIMITED = Int.MAX_VALUE

        private val partDirectory = SessionPartDirectory(
            timestamp = TIMESTAMP,
            uuid = UUID,
            userSessionId = USER_SESSION_ID,
            sessionPartId = SESSION_PART_ID,
        )

        private val completed = listOf(fullyPopulatedSpan, inFlightSpan)

        private fun spans(count: Int): List<Span> = (0 until count).map {
            fullyPopulatedSpan.copy(spanId = "aaaaaaaaaaaaaa$it", name = "span$it")
        }
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var logger: FakeInternalLogger
    private lateinit var telemetryService: FakeTelemetryService
    private lateinit var writer: CompletedSpansWriter

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        telemetryService = FakeTelemetryService()
        activePart = partDirectory
        writer = createWriter()
        createPartDir(partDirectory)
    }

    @Test
    fun `the completed spans are appended into the session part directory`() {
        assertTrue(append())
        assertTrue(completedSpansFile().isFile)
        assertEquals(listOf(COMPLETED_SPANS_FILE_NAME), partDir().list()?.toList())
        assertNoInternalErrors()
    }

    @Test
    fun `every span field is persisted`() {
        append(span = listOf(fullyPopulatedSpan))
        assertEquals(CompletedSpans(spans = listOf(fullyPopulatedSpanProto)), readCompletedSpans())
    }

    @Test
    fun `spans are appended in order as one record each, batched or not`() {
        val batch = listOf("first", "second", "third").map { fullyPopulatedSpan.copy(name = it) }
        assertTrue(append(span = batch))
        assertEquals(batch.map(Span::toProto), readCompletedSpans().spans)
        val batched = completedSpansFile().readBytes()

        sessionsDir = tempFolder.newFolder("one_at_a_time")
        writer = createWriter()
        createPartDir(partDirectory)
        batch.forEach { assertTrue(writer.append(listOf(it))) }

        assertEquals(batch.map(Span::toProto), readCompletedSpans().spans)
        assertArrayEquals(batched, completedSpansFile().readBytes())
        assertNoInternalErrors()
    }

    @Test
    fun `an empty list appends nothing and leaves the log alone`() {
        assertTrue(append(span = emptyList()))
        assertFalse(completedSpansFile().exists())

        assertTrue(append())
        assertTrue(writer.append(emptyList()))
        assertEquals(completed.map(Span::toProto), readCompletedSpans().spans)
        assertNoInternalErrors()
    }

    @Test
    fun `a bare span and an unended span round-trip`() {
        assertTrue(append(span = listOf(Span(), inFlightSpan)))

        val decoded = readCompletedSpans().spans
        assertEquals(listOf(SpanProto(), inFlightSpanProto), decoded)
        assertNull(decoded.last().end_time_unix_nano)
        assertNoInternalErrors()
    }

    @Test
    fun `nothing is appended when no session part is active`() {
        assertFalse(append(directory = null))
        assertFalse(completedSpansFile().exists())

        assertTrue(append(span = listOf(fullyPopulatedSpan)))
        activePart = null
        assertFalse(writer.append(listOf(fullyPopulatedSpan.copy(name = "other"))))
        assertEquals(listOf(fullyPopulatedSpanProto), readCompletedSpans().spans)
        assertNoInternalErrors()
    }

    @Test
    fun `empty session ids are supported`() {
        val directory = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID)
        createPartDir(directory)
        assertTrue(append(directory))
        assertEquals(completed.map(Span::toProto), readCompletedSpans(directory).spans)
    }

    @Test
    fun `each session part directory gets its own log`() {
        val other = otherPartDirectory()
        createPartDir(other)

        assertTrue(append(span = listOf(fullyPopulatedSpan)))
        assertTrue(append(other, listOf(fullyPopulatedSpan.copy(spanId = "aaaaaaaaaaaaaaa9"))))

        assertEquals("aaaaaaaaaaaaaaa1", readCompletedSpans().spans.single().span_id)
        assertEquals("aaaaaaaaaaaaaaa9", readCompletedSpans(other).spans.single().span_id)
        assertNoInternalErrors()
    }

    @Test
    fun `the span limit is enforced within a batch and across appends`() {
        writer = createWriter(maxSpans = 3)
        assertTrue(append(span = spans(2)))
        assertTrue(writer.append(spans(2))) // one fits, one is dropped
        assertTrue(writer.append(spans(1))) // the limit is reached, so all are dropped

        assertEquals(listOf("span0", "span1", "span0"), readCompletedSpans().spans.map { it.name })
        assertAppliedLimitDrops(2)
        assertNoInternalErrors()
    }

    @Test
    fun `a limit of zero drops everything and creates no file`() {
        writer = createWriter(maxSpans = 0)
        assertTrue(append(span = spans(2)))
        assertFalse(completedSpansFile().exists())
        assertAppliedLimitDrops(2)
        assertNoInternalErrors()
    }

    @Test
    fun `the span count resets when a new session part becomes active`() {
        val other = otherPartDirectory()
        createPartDir(other)
        writer = createWriter(maxSpans = 2)

        assertTrue(append(span = spans(3)))
        assertAppliedLimitDrops(1)

        assertTrue(append(other, spans(2)))
        assertEquals(listOf("span0", "span1"), readCompletedSpans(other).spans.map { it.name })
        assertAppliedLimitDrops(1)
    }

    @Test
    fun `a session part path that is not a directory is reported`() {
        val absent = SessionPartDirectory(timestamp = TIMESTAMP + 2, uuid = UUID)
        assertFalse(append(absent))
        assertFalse(partDir(absent).exists())
        assertWriteFailureTracked()
        logger.internalErrorMessages.clear()

        val occupied = SessionPartDirectory(timestamp = TIMESTAMP + 3, uuid = UUID)
        val occupyingFile = partDir(occupied).apply { writeText("not a directory") }
        assertFalse(append(occupied))
        assertWriteFailureTracked()
        assertEquals("not a directory", occupyingFile.readText())
    }

    @Test
    fun `a failing session part source is reported and does not throw`() {
        writer = createWriter(sessionPartDirectorySource = { error("boom") })
        assertFalse(writer.append(completed))
        assertEquals(emptyList<String>(), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `failure building a record leaves no partial record on disk`() {
        assertFalse(append(span = listOf(Span(attributes = ExplodingList()))))
        assertEquals(listOf(COMPLETED_SPANS_FILE_NAME), partDir().list()?.toList())
        assertEquals(0L, completedSpansFile().length())
        assertEquals(CompletedSpans(), readCompletedSpans())
        assertWriteFailureTracked()
    }

    @Test
    fun `bad spans are skipped but only the first is reported`() {
        val batch = listOf(
            Span(attributes = ExplodingList()),
            fullyPopulatedSpan,
            Span(attributes = ExplodingList()),
            inFlightSpan,
        )
        assertFalse(append(span = batch))
        assertEquals(listOf(fullyPopulatedSpanProto, inFlightSpanProto), readCompletedSpans().spans)
        assertWriteFailureTracked()
    }

    @Test
    fun `a failed write is reported once and consumes no span budget`() {
        writer = createWriter(maxSpans = 2)
        assertTrue(append(span = listOf(fullyPopulatedSpan)))

        val stream = ExplodingStream()
        assertFalse(writer.appendSpan(inFlightSpan, stream))
        assertFalse(writer.appendSpan(inFlightSpan, stream))
        assertWriteFailureTracked()
        logger.internalErrorMessages.clear()

        assertTrue(writer.append(listOf(inFlightSpan)))
        assertEquals(listOf(fullyPopulatedSpanProto, inFlightSpanProto), readCompletedSpans().spans)
        assertAppliedLimitDrops(0)
    }

    @Test
    fun `a directory occupying the log path is reported`() {
        File(partDir(), COMPLETED_SPANS_FILE_NAME).mkdirs()
        assertFalse(append())
        assertWriteFailureTracked()
    }

    @Test
    fun `a bad span in a later append is not reported again and leaves the log intact`() {
        assertTrue(append(span = listOf(fullyPopulatedSpan)))
        assertFalse(writer.append(listOf(Span(attributes = ExplodingList()))))
        assertFalse(writer.append(listOf(Span(attributes = ExplodingList()))))

        assertEquals(listOf(fullyPopulatedSpanProto), readCompletedSpans().spans)
        assertEquals(listOf(COMPLETED_SPANS_FILE_NAME), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `a bad span is reported again once a new session part becomes active`() {
        val other = otherPartDirectory()
        createPartDir(other)

        assertFalse(append(span = listOf(Span(attributes = ExplodingList()))))
        assertFalse(append(other, listOf(Span(attributes = ExplodingList()))))

        assertEquals(2, logger.internalErrorMessages.size)
        assertEquals(listOf("CompletedSpansWriteFail"), logger.internalErrorMessages.map { it.msg }.distinct())
    }

    @Test
    fun `a bad span does not consume span budget`() {
        writer = createWriter(maxSpans = 2)
        assertFalse(append(span = listOf(Span(attributes = ExplodingList()), fullyPopulatedSpan)))
        logger.internalErrorMessages.clear()

        assertTrue(writer.append(listOf(inFlightSpan)))
        assertEquals(listOf(fullyPopulatedSpanProto, inFlightSpanProto), readCompletedSpans().spans)
        assertAppliedLimitDrops(0)
        assertNoInternalErrors()
    }

    @Test
    fun `concurrent appends leave one valid log holding every span`() {
        val threadCount = 8
        val appendsPerThread = 25

        val latch = CountDownLatch(1)
        val threads = (0 until threadCount).map { thread ->
            Thread {
                latch.await()
                repeat(appendsPerThread) { index ->
                    writer.append(listOf(fullyPopulatedSpan.copy(name = "span-$thread-$index")))
                }
            }
        }
        threads.forEach(Thread::start)
        latch.countDown()
        threads.forEach(Thread::join)

        val names = readCompletedSpans().spans.map { it.name }
        assertEquals(threadCount * appendsPerThread, names.size)
        assertEquals(names.size, names.toSet().size)
        assertEquals(listOf(COMPLETED_SPANS_FILE_NAME), partDir().list()?.toList())
        assertAppliedLimitDrops(0)
        assertNoInternalErrors()
    }

    private fun createWriter(
        maxSpans: Int = UNLIMITED,
        sessionPartDirectorySource: () -> SessionPartDirectory? = { activePart },
    ): CompletedSpansWriter = CompletedSpansWriter(
        lazy { sessionsDir },
        sessionPartDirectorySource,
        maxSpans,
        telemetryService,
        logger,
    )

    private fun otherPartDirectory() = SessionPartDirectory(
        timestamp = TIMESTAMP + 1,
        uuid = "d3721de2-490a-533b-cacd-36423d8b6aab",
        userSessionId = "cccccccccccccccccccccccccccccccc",
        sessionPartId = "dddddddddddddddddddddddddddddddd",
    )

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File =
        File(sessionsDir, directory.dirName)

    private fun completedSpansFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), COMPLETED_SPANS_FILE_NAME)

    private fun readCompletedSpans(directory: SessionPartDirectory = partDirectory): CompletedSpans =
        completedSpansFile(directory).inputStream().use(CompletedSpans.ADAPTER::decode)

    private fun append(
        directory: SessionPartDirectory? = partDirectory,
        span: List<Span> = completed,
    ): Boolean {
        activePart = directory
        return writer.append(span)
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertWriteFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("CompletedSpansWriteFail", logger.internalErrorMessages.single().msg)
    }

    private fun assertAppliedLimitDrops(count: Int) {
        val expected = List(count) { "persisted_span" to AppliedLimitType.DROP }
        assertEquals(expected, telemetryService.appliedLimits.toList())
    }

    private class ExplodingList : List<Attribute> by emptyList() {
        override fun iterator(): Iterator<Attribute> = error("boom")
    }

    /**
     * A stream that fails on every write, standing in for a file that cannot be written to.
     */
    private class ExplodingStream : OutputStream() {
        override fun write(b: Int): Unit = throw IOException("boom")
        override fun write(b: ByteArray, off: Int, len: Int): Unit = throw IOException("boom")
    }
}
