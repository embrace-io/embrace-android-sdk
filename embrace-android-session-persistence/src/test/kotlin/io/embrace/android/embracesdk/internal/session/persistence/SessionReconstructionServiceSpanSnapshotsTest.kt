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

internal class SessionReconstructionServiceSpanSnapshotsTest {

    private companion object {
        private const val SPAN_SNAPSHOTS_FILE_NAME = "span_snapshots.pb"
        private const val ENVELOPE_VERSION = "0.1.0"
        private const val ENVELOPE_TYPE = "spans"
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

        private val secondSnapshot = inFlightSpan.copy(spanId = "aaaaaaaaaaaaaaa5")

        private val endedSnapshot = inFlightSpan.copy(
            spanId = "aaaaaaaaaaaaaaa6",
            endTimeNanos = 1726739283900000000L,
            status = Span.Status.OK,
        )
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
    fun `span snapshots are reconstructed in the order they were written`() {
        write(snapshots = listOf(inFlightSpan, secondSnapshot))

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(inFlightSpan, secondSnapshot), payload.spanSnapshots)
        assertNoInternalErrors()
    }

    @Test
    fun `a finished session span leaves the persisted snapshots alone`() {
        write(snapshots = listOf(inFlightSpan))

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(inFlightSpan), payload.spanSnapshots)
        assertEquals(listOf(fullyPopulatedSpan), payload.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `an unfinished session span is reconstructed after the persisted snapshots`() {
        val incomplete = fullyPopulatedSpan.copy(endTimeNanos = null)
        sessionSpan = incomplete
        write(snapshots = listOf(inFlightSpan))

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(inFlightSpan, incomplete), payload.spanSnapshots)
        assertEquals(emptyList<Span>(), payload.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `an empty snapshots file reconstructs no snapshots when the session span finished`() {
        write()

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(emptyList<Span>(), payload.spanSnapshots)
        assertEquals(listOf(fullyPopulatedSpan), payload.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `an empty snapshots file reconstructs the unfinished session span alone`() {
        val incomplete = fullyPopulatedSpan.copy(endTimeNanos = null)
        sessionSpan = incomplete
        write()

        assertEquals(listOf(incomplete), service.reconstruct(partDirectory)?.data?.spanSnapshots)
        assertNoInternalErrors()
    }

    @Test
    fun `a snapshot carrying an end time is still reconstructed as a snapshot`() {
        write(snapshots = listOf(endedSnapshot))

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(endedSnapshot), payload.spanSnapshots)
        assertEquals(listOf(fullyPopulatedSpan), payload.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `the latest span snapshots are reconstructed after they are overwritten`() {
        write(snapshots = listOf(inFlightSpan))
        writeSpanSnapshots(snapshots = listOf(secondSnapshot))

        assertEquals(listOf(secondSnapshot), service.reconstruct(partDirectory)?.data?.spanSnapshots)
        assertNoInternalErrors()
    }

    @Test
    fun `each session part directory reconstructs its own span snapshots`() {
        val other = SessionPartDirectory(
            timestamp = TIMESTAMP + 1,
            uuid = "d3721de2-490a-533b-cacd-36423d8b6aab",
            userSessionId = "cccccccccccccccccccccccccccccccc",
            sessionPartId = "dddddddddddddddddddddddddddddddd",
        )
        createPartDir(other)
        write(snapshots = listOf(inFlightSpan))
        write(other, snapshots = listOf(secondSnapshot))

        assertEquals(listOf(inFlightSpan), service.reconstruct(partDirectory)?.data?.spanSnapshots)
        assertEquals(listOf(secondSnapshot), service.reconstruct(other)?.data?.spanSnapshots)
        assertNoInternalErrors()
    }

    @Test
    fun `a missing snapshots file reconstructs no snapshots`() {
        writeManifest()
        writeMetadata()
        writeSessionSpan()
        writeCompletedSpans()

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(emptyList<Span>(), payload.spanSnapshots)
        assertEquals(listOf(fullyPopulatedSpan), payload.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `a missing snapshots file still reconstructs the unfinished session span`() {
        val incomplete = fullyPopulatedSpan.copy(endTimeNanos = null)
        sessionSpan = incomplete
        writeManifest()
        writeMetadata()
        writeSessionSpan()
        writeCompletedSpans()

        val payload = checkNotNull(service.reconstruct(partDirectory)?.data)
        assertEquals(listOf(incomplete), payload.spanSnapshots)
        assertEquals(emptyList<Span>(), payload.spans)
        assertNoInternalErrors()
    }

    @Test
    fun `a directory occupying the snapshots path is reported`() {
        writeManifest()
        writeMetadata()
        writeSessionSpan()
        writeCompletedSpans()
        snapshotsFile().mkdirs()

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a truncated snapshots file is reported and does not throw`() {
        write(snapshots = listOf(inFlightSpan))
        val bytes = snapshotsFile().readBytes()
        snapshotsFile().writeBytes(bytes.copyOf(bytes.size / 2))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `a snapshots file holding arbitrary bytes is reported and does not throw`() {
        write()
        snapshotsFile().writeBytes(byteArrayOf(-1, -1, -1, -1, -1, -1))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `an empty snapshots file is reported`() {
        write()
        snapshotsFile().writeBytes(byteArrayOf())

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `snapshots holding no format version are reported`() {
        write()
        writeSnapshotsBytes(fullyPopulatedSpanSnapshotsProto.copy(format_version = 0))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    @Test
    fun `an unsupported snapshots format version is reported`() {
        write()
        writeSnapshotsBytes(fullyPopulatedSpanSnapshotsProto.copy(format_version = FORMAT_VERSION + 1))

        assertNull(service.reconstruct(partDirectory))
        assertReconstructionFailureTracked()
    }

    private fun createPartDir(directory: SessionPartDirectory): File =
        File(sessionsDir, directory.dirName).apply { mkdirs() }

    private fun partDir(directory: SessionPartDirectory = partDirectory): File =
        File(sessionsDir, directory.dirName)

    private fun snapshotsFile(directory: SessionPartDirectory = partDirectory): File =
        File(partDir(directory), SPAN_SNAPSHOTS_FILE_NAME)

    private fun write(
        directory: SessionPartDirectory = partDirectory,
        snapshots: List<Span> = emptyList(),
    ) {
        writeManifest(directory)
        writeMetadata(directory)
        writeSessionSpan(directory)
        writeCompletedSpans(directory)
        writeSpanSnapshots(directory, snapshots)
    }

    private fun writeManifest(directory: SessionPartDirectory = partDirectory) {
        assertTrue(manifestWriter.write(directory, fullyPopulatedResource, ENVELOPE_VERSION, ENVELOPE_TYPE))
    }

    private fun writeMetadata(directory: SessionPartDirectory = partDirectory) {
        activePart = directory
        assertTrue(metadataWriter.write())
    }

    private fun writeSessionSpan(directory: SessionPartDirectory = partDirectory) {
        activePart = directory
        assertTrue(sessionSpanWriter.write(sessionSpan))
    }

    private fun writeCompletedSpans(directory: SessionPartDirectory = partDirectory) {
        File(partDir(directory), "completed_spans.pb").writeBytes(completedSpansLog(emptyList()))
    }

    private fun writeSpanSnapshots(
        directory: SessionPartDirectory = partDirectory,
        snapshots: List<Span> = emptyList(),
    ) {
        activePart = directory
        assertTrue(snapshotsWriter.write(snapshots))
    }

    private fun writeSnapshotsBytes(
        snapshots: SpanSnapshots,
        directory: SessionPartDirectory = partDirectory,
    ) {
        snapshotsFile(directory).writeBytes(SpanSnapshots.ADAPTER.encode(snapshots))
    }

    private fun assertNoInternalErrors() {
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    private fun assertReconstructionFailureTracked() {
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals("SessionReconstructionFail", logger.internalErrorMessages.single().msg)
    }
}
