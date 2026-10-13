package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class FileModifiedTimeLastAliveStoreTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var partDir: File
    private lateinit var clock: FakeClock
    private lateinit var logger: FakeInternalLogger
    private lateinit var store: FileModifiedTimeLastAliveStore
    private lateinit var target: SessionPartWriteTarget
    private lateinit var service: SessionReconstructionService

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        partDir = File(sessionsDir, partDirectory.dirName).apply { mkdirs() }
        clock = FakeClock()
        logger = FakeInternalLogger(throwOnInternalError = false)
        store = FileModifiedTimeLastAliveStore(clock)
        target = SessionPartWriteTarget(lazy { sessionsDir }, store) { partDirectory }
        service = SessionReconstructionService(lazy { sessionsDir }, logger, store)
    }

    @Test
    fun `each write of a part file moves the part's last alive time to the clock's time`() {
        SessionMetadataWriter(
            target = target,
            metadataSource = { fullyPopulatedMetadata },
            resourceSource = { fullyPopulatedResource },
            envelopeVersion = "0.1.0",
            envelopeType = "spans",
            sharedLibSymbolMappingSource = { null },
            logger = logger,
        ).write()
        assertEquals(clock.now(), service.lastAliveMs(partDirectory))

        clock.tick(5_000)
        CompletedSpansWriter(target, logger).write(listOf(fullyPopulatedSpan))
        assertEquals(clock.now(), service.lastAliveMs(partDirectory))

        val snapshots = SpanSnapshotsWriter(target, logger)
        clock.tick(5_000)
        snapshots.write(listOf(inFlightSpan))
        assertEquals(clock.now(), service.lastAliveMs(partDirectory))

        clock.tick(5_000)
        snapshots.append(listOf(inFlightSpan.copy(name = "renamed"))) { emptyList() }
        assertEquals(clock.now(), service.lastAliveMs(partDirectory))
    }

    @Test
    fun `the latest modification time across the part's files is read back`() {
        File(partDir, METADATA_FILE_NAME).apply { writeText("metadata") }.setLastModified(2_000_000L)
        File(partDir, SPAN_SNAPSHOTS_FILE_NAME).apply { writeText("snapshots") }.setLastModified(5_000_000L)
        File(partDir, COMPLETED_SPANS_FILE_NAME).apply { writeText("completed") }.setLastModified(3_000_000L)

        assertEquals(5_000_000L, store.lastAliveMs(partDir))
    }

    @Test
    fun `recording a file that does not exist yet does nothing`() {
        store.recorder(partDir).recordAlive(File(partDir, METADATA_FILE_NAME))

        assertNull(store.lastAliveMs(partDir))
    }

    @Test
    fun `a part with no files on disk has no last alive time`() {
        assertNull(service.lastAliveMs(partDirectory))
        assertNull(service.lastAliveMs(partDirectory.copy(uuid = "d2610cd1-389f-422a-bfbc-25312c7a599a")))
    }

    private companion object {
        private val partDirectory = SessionPartDirectory(
            timestamp = FakeClock.DEFAULT_FAKE_CURRENT_TIME,
            uuid = "c2610cd1-389f-422a-bfbc-25312c7a599a",
            userSessionId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            sessionPartId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        )
    }
}
