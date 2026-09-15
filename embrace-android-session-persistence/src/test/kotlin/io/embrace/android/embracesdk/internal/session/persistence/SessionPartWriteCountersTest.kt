package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeSectionRecorder
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The writers for a session part publish what they put on disk as system trace counters, so that a
 * capture shows the write amplification of a session.
 */
internal class SessionPartWriteCountersTest {

    private companion object {
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"

        private val partDirectory = SessionPartDirectory(timestamp = TIMESTAMP, uuid = UUID)
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var recorder: FakeSectionRecorder
    private lateinit var target: SessionPartWriteTarget
    private lateinit var completedSpansWriter: CompletedSpansWriter
    private lateinit var spanSnapshotsWriter: SpanSnapshotsWriter

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        File(sessionsDir, partDirectory.dirName).mkdirs()
        recorder = FakeSectionRecorder()
        SystemTrace.recorder = recorder

        val logger = FakeInternalLogger(throwOnInternalError = false)
        target = SessionPartWriteTarget(lazy { sessionsDir }) { partDirectory }
        completedSpansWriter = CompletedSpansWriter(target, logger)
        spanSnapshotsWriter = SpanSnapshotsWriter(target, logger)
    }

    @After
    fun tearDown() {
        SystemTrace.recorder = null
    }

    @Test
    fun `nothing is counted before anything is written`() {
        assertNull(recorder.latestCounter(MULTI_FILE_BYTES_COUNTER))
        assertNull(recorder.latestCounter(MULTI_FILE_FILES_COUNTER))
    }

    @Test
    fun `an append to the completed span log counts the bytes it added`() {
        assertTrue(completedSpansWriter.write(listOf(fullyPopulatedSpan)))

        assertEquals(fileLength(COMPLETED_SPANS_FILE_NAME), recorder.latestCounter(MULTI_FILE_BYTES_COUNTER))
        assertEquals(1L, recorder.latestCounter(MULTI_FILE_FILES_COUNTER))
    }

    @Test
    fun `each append counts as a write of its own, so the totals show the cost of appending`() {
        completedSpansWriter.write(listOf(fullyPopulatedSpan))
        val afterFirst = recorder.latestCounter(MULTI_FILE_BYTES_COUNTER)
        completedSpansWriter.write(listOf(fullyPopulatedSpan))

        assertEquals(fileLength(COMPLETED_SPANS_FILE_NAME), recorder.latestCounter(MULTI_FILE_BYTES_COUNTER))
        assertEquals(2L, recorder.latestCounter(MULTI_FILE_FILES_COUNTER))
        assertTrue(checkNotNull(recorder.latestCounter(MULTI_FILE_BYTES_COUNTER)) > checkNotNull(afterFirst))
    }

    @Test
    fun `appending no spans is not counted as a write`() {
        assertTrue(completedSpansWriter.write(emptyList()))

        assertNull(recorder.latestCounter(MULTI_FILE_FILES_COUNTER))
    }

    @Test
    fun `a span snapshot rollup counts the bytes the file was rewritten with`() {
        assertTrue(spanSnapshotsWriter.write(listOf(inFlightSpan)))

        assertEquals(fileLength(SPAN_SNAPSHOTS_FILE_NAME), recorder.latestCounter(MULTI_FILE_BYTES_COUNTER))
        assertEquals(1L, recorder.latestCounter(MULTI_FILE_FILES_COUNTER))
    }

    @Test
    fun `every writer for a session part adds to the same running total`() {
        spanSnapshotsWriter.write(listOf(inFlightSpan))
        completedSpansWriter.write(listOf(fullyPopulatedSpan))

        val expected = fileLength(SPAN_SNAPSHOTS_FILE_NAME) + fileLength(COMPLETED_SPANS_FILE_NAME)
        assertEquals(expected, recorder.latestCounter(MULTI_FILE_BYTES_COUNTER))
        assertEquals(2L, recorder.latestCounter(MULTI_FILE_FILES_COUNTER))
    }

    private fun fileLength(fileName: String): Long =
        File(File(sessionsDir, partDirectory.dirName), fileName).length()
}
