package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class SessionReconstructionServiceLastWrittenTest {

    private companion object {
        private const val METADATA_FILE_NAME = "metadata.pb"
        private const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"
        private const val SPAN_SNAPSHOTS_FILE_NAME = "span_snapshots.pb"
        private const val TIMESTAMP = 1726739283136L
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"

        private val partDirectory = SessionPartDirectory(
            timestamp = TIMESTAMP,
            uuid = UUID,
            userSessionId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            sessionPartId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        )
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var metadataWriter: SessionMetadataWriter
    private lateinit var completedWriter: CompletedSpansWriter
    private lateinit var snapshotsWriter: SpanSnapshotsWriter
    private lateinit var service: SessionReconstructionService

    @Volatile
    private var userId: String = "userId"

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        clock = FakeClock()
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        userId = "userId"
        val target = SessionPartWriteTarget(lazy { sessionsDir }, clock) { partDirectory }
        metadataWriter = SessionMetadataWriter(
            target = target,
            metadataSource = { fullyPopulatedMetadata.copy(userId = userId) },
            resourceSource = { fullyPopulatedResource },
            envelopeVersion = "0.1.0",
            envelopeType = "spans",
            sharedLibSymbolMappingSource = { null },
            errorHandler = errorHandler,
        )
        completedWriter = CompletedSpansWriter(target, errorHandler)
        snapshotsWriter = SpanSnapshotsWriter(target, errorHandler)
        service = SessionReconstructionService(lazy { sessionsDir }, errorHandler)
        File(sessionsDir, partDirectory.dirName).mkdirs()
    }

    @Test
    fun `a part holding only metadata was last written when the metadata was`() {
        assertTrue(metadataWriter.write())
        assertEquals(clock.now(), lastWrittenMs())
        assertNoSdkErrors()
    }

    @Test
    fun `changed metadata moves when the part was last written`() {
        assertTrue(metadataWriter.write())
        clock.tick(1000)
        userId = "otherUserId"
        assertTrue(metadataWriter.write())
        assertEquals(clock.now(), lastWrittenMs())
    }

    @Test
    fun `metadata that has not changed does not move when the part was last written`() {
        assertTrue(metadataWriter.write())
        val writtenMs = clock.now()
        clock.tick(1000)
        assertTrue(metadataWriter.write())
        assertEquals(writtenMs, lastWrittenMs())
    }

    @Test
    fun `each append to the completed spans moves when the part was last written`() {
        assertTrue(metadataWriter.write())
        clock.tick(1000)
        assertTrue(completedWriter.write(listOf(fullyPopulatedSpan)))
        assertEquals(clock.now(), lastWrittenMs())

        clock.tick(1000)
        assertTrue(completedWriter.write(listOf(fullyPopulatedSpan.copy(spanId = "aaaaaaaaaaaaaaa9"))))
        assertEquals(clock.now(), lastWrittenMs())
        assertNoSdkErrors()
    }

    @Test
    fun `a rollup and an append of the span snapshots each move when the part was last written`() {
        assertTrue(metadataWriter.write())
        clock.tick(1000)
        assertTrue(snapshotsWriter.write(listOf(inFlightSpan)))
        assertEquals(clock.now(), lastWrittenMs())

        clock.tick(1000)
        val renamed = inFlightSpan.copy(name = "renamed")
        assertTrue(snapshotsWriter.append(listOf(renamed)) { listOf(renamed) })
        assertEquals(clock.now(), lastWrittenMs())
        assertNoSdkErrors()
    }

    @Test
    fun `the latest write across the files is when the part was last written`() {
        assertTrue(metadataWriter.write())
        clock.tick(1000)
        assertTrue(snapshotsWriter.write(listOf(inFlightSpan)))
        clock.tick(1000)
        assertTrue(completedWriter.write(listOf(fullyPopulatedSpan)))
        val latestMs = clock.now()
        clock.tick(1000)
        assertTrue(metadataWriter.write())

        assertEquals(latestMs, lastWrittenMs())
    }

    @Test
    fun `an append torn before its write time keeps the time of the last whole write`() {
        assertTrue(metadataWriter.write())
        clock.tick(1000)
        assertTrue(completedWriter.write(listOf(fullyPopulatedSpan)))
        val wholeWriteMs = clock.now()
        clock.tick(1000)
        assertTrue(completedWriter.write(listOf(fullyPopulatedSpan.copy(spanId = "aaaaaaaaaaaaaaa9"))))
        completedWriter.close()

        val log = partFile(COMPLETED_SPANS_FILE_NAME)
        log.writeBytes(log.readBytes().copyOf(log.length().toInt() - 1))

        val part = checkNotNull(service.reconstruct(partDirectory))
        assertEquals(2, part.envelope.data.spans?.size)
        assertEquals(wholeWriteMs, part.lastWrittenMs)
    }

    @Test
    fun `a part whose files hold no write time has no last written time`() {
        partFile(METADATA_FILE_NAME).writeBytes(SessionMetadata.ADAPTER.encode(fullyPopulatedMetadataProto()))
        partFile(COMPLETED_SPANS_FILE_NAME).writeBytes(completedSpansLog(listOf(fullyPopulatedSpanProto)))
        partFile(SPAN_SNAPSHOTS_FILE_NAME).writeBytes(
            SpanCollection.ADAPTER.encode(
                SpanCollection(format_version = FORMAT_VERSION, spans = listOf(inFlightSpanProto)),
            ),
        )

        val part = checkNotNull(service.reconstruct(partDirectory))
        assertEquals(listOf(fullyPopulatedSpan), part.envelope.data.spans)
        assertNull(part.lastWrittenMs)
        assertNoSdkErrors()
    }

    private fun lastWrittenMs(): Long? = checkNotNull(service.reconstruct(partDirectory)).lastWrittenMs

    private fun partFile(fileName: String): File = File(File(sessionsDir, partDirectory.dirName), fileName)

    private fun assertNoSdkErrors() {
        assertEquals(emptyList<FakeErrorHandler.LogMessage>(), errorHandler.sdkErrorMessages)
    }
}
