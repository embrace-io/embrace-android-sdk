package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch

internal class SessionSpanWriterTest {

    private companion object {
        private const val SESSION_SPAN_FILE_NAME = "session_span.pb"
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
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var logger: FakeInternalLogger
    private lateinit var writer: SessionSpanWriter

    @Volatile
    private var activePart: SessionPartDirectory? = partDirectory

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        logger = FakeInternalLogger(throwOnInternalError = false)
        activePart = partDirectory
        writer = SessionSpanWriter(lazy { sessionsDir }, { activePart }, logger)
        createPartDir(partDirectory)
    }

    @Test
    fun `the session span is written into the session part directory`() {
        assertTrue(write())
        assertTrue(sessionSpanFile().isFile)
        assertNoInternalErrors()
    }

    @Test
    fun `every span field is persisted`() {
        write()

        val sessionSpan = readSessionSpan()
        assertEquals(fullyPopulatedSessionSpanProto, sessionSpan)
        assertEquals(fullyPopulatedSpanProto, sessionSpan.span)
    }

    @Test
    fun `the format version is persisted even when nothing else is populated`() {
        assertTrue(write(span = Span()))
        val sessionSpan = readSessionSpan()
        assertEquals(FORMAT_VERSION, sessionSpan.format_version)
        assertEquals(SpanProto(), sessionSpan.span)
    }

    @Test
    fun `the session span is overwritten as its attributes change`() {
        write()
        val updated = fullyPopulatedSpan.copy(
            attributes = listOf(Attribute(key = "emb.heartbeat_time_unix_nano", data = "1726739286136000000")),
        )
        assertTrue(writer.write(updated))
        assertEquals(updated.toProto(), readSessionSpan().span)
        assertEquals(listOf(SESSION_SPAN_FILE_NAME), partDir().list()?.toList())
        assertNoInternalErrors()
    }

    @Test
    fun `nothing is written when no session part is active`() {
        assertFalse(write(directory = null))
        assertFalse(sessionSpanFile().exists())
        assertNoInternalErrors()
    }

    @Test
    fun `session span changes are ignored once the session part is no longer active`() {
        write()
        activePart = null
        assertFalse(writer.write(fullyPopulatedSpan.copy(name = "other")))
        assertEquals("emb-session", readSessionSpan().span?.name)
        assertNoInternalErrors()
    }

    @Test
    fun `empty session ids are supported`() {
        val directory = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID)
        createPartDir(directory)
        assertTrue(write(directory))
        assertEquals(fullyPopulatedSessionSpanProto, readSessionSpan(directory))
    }

    @Test
    fun `each session part directory gets its own session span`() {
        val other = SessionPartDirectory(
            timestamp = TIMESTAMP + 1,
            uuid = "d3721de2-490a-533b-cacd-36423d8b6aab",
            userSessionId = "cccccccccccccccccccccccccccccccc",
            sessionPartId = "dddddddddddddddddddddddddddddddd",
        )
        createPartDir(other)

        assertTrue(write())
        assertTrue(write(other, fullyPopulatedSpan.copy(spanId = "aaaaaaaaaaaaaaa9")))
        assertEquals("aaaaaaaaaaaaaaa1", readSessionSpan().span?.span_id)
        assertEquals("aaaaaaaaaaaaaaa9", readSessionSpan(other).span?.span_id)
        assertNoInternalErrors()
    }

    @Test
    fun `no temporary files are left behind`() {
        write()
        writer.write(fullyPopulatedSpan)
        assertEquals(listOf(SESSION_SPAN_FILE_NAME), partDir().list()?.toList())
    }

    @Test
    fun `a stale temporary file does not prevent a write`() {
        File(partDir(), "${SESSION_SPAN_FILE_NAME}1234.tmp").writeText("torn write")
        assertTrue(write())
        assertEquals(fullyPopulatedSessionSpanProto, readSessionSpan())
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
        writer = SessionSpanWriter(lazy { sessionsDir }, { error("boom") }, logger)
        assertFalse(writer.write(fullyPopulatedSpan))
        assertEquals(emptyList<String>(), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `failure building the session span leaves no files on disk`() {
        assertFalse(write(span = Span(attributes = ExplodingList())))
        assertEquals(emptyList<String>(), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `a failed overwrite leaves the previous session span intact`() {
        assertTrue(write())
        assertFalse(writer.write(Span(attributes = ExplodingList())))
        assertEquals(fullyPopulatedSessionSpanProto, readSessionSpan())
        assertEquals(listOf(SESSION_SPAN_FILE_NAME), partDir().list()?.toList())
        assertWriteFailureTracked()
    }

    @Test
    fun `concurrent session span writes leave one valid file`() {
        val threadCount = 8
        val writesPerThread = 25
        write()

        val spanIds = (0 until threadCount).map { "aaaaaaaaaaaaaaa$it" }
        val latch = CountDownLatch(1)
        val threads = spanIds.map { spanId ->
            Thread {
                latch.await()
                repeat(writesPerThread) {
                    writer.write(fullyPopulatedSpan.copy(spanId = spanId))
                }
            }
        }
        threads.forEach(Thread::start)
        latch.countDown()
        threads.forEach(Thread::join)

        assertTrue(checkNotNull(readSessionSpan().span).span_id in spanIds)
        assertEquals(listOf(SESSION_SPAN_FILE_NAME), partDir().list()?.toList())
        assertNoInternalErrors()
    }

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File =
        File(sessionsDir, directory.dirName)

    private fun sessionSpanFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), SESSION_SPAN_FILE_NAME)

    private fun readSessionSpan(directory: SessionPartDirectory = partDirectory): SessionPartSpan =
        sessionSpanFile(directory).inputStream().use(SessionPartSpan.ADAPTER::decode)

    private fun write(
        directory: SessionPartDirectory? = partDirectory,
        span: Span = fullyPopulatedSpan,
    ): Boolean {
        activePart = directory
        return writer.write(span)
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertWriteFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionSpanWriteFail", logger.internalErrorMessages.single().msg)
    }

    /**
     * A list that fails when it is read, standing in for any input that blows up while the session
     * span is being built.
     */
    private class ExplodingList : List<Attribute> by emptyList() {
        override fun iterator(): Iterator<Attribute> = error("boom")
    }
}
