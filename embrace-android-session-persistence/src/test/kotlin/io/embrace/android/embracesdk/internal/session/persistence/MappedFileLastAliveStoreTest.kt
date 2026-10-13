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
import java.io.RandomAccessFile

internal class MappedFileLastAliveStoreTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var partDir: File
    private lateinit var lastAliveFile: File
    private lateinit var clock: FakeClock
    private lateinit var logger: FakeInternalLogger
    private lateinit var store: MappedFileLastAliveStore
    private lateinit var target: SessionPartWriteTarget
    private lateinit var service: SessionReconstructionService

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        partDir = File(sessionsDir, partDirectory.dirName).apply { mkdirs() }
        lastAliveFile = File(partDir, LAST_ALIVE_FILE_NAME)
        clock = FakeClock()
        logger = FakeInternalLogger(throwOnInternalError = false)
        store = MappedFileLastAliveStore(clock)
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
    fun `opening a recorder records when it was opened`() {
        val openedMs = clock.now()
        store.recorder(partDir)
        assertEquals(openedMs, store.lastAliveMs(partDir))
    }

    @Test
    fun `the recorded time does not depend on any file's modification time`() {
        val recorder = store.recorder(partDir)
        clock.tick(5_000)
        recorder.recordAlive(File(partDir, METADATA_FILE_NAME))

        lastAliveFile.setLastModified(1_000L)
        assertEquals(clock.now(), store.lastAliveMs(partDir))
    }

    @Test
    fun `a time too far past the recorder opening for 32 bits is capped`() {
        val openedMs = clock.now()
        val recorder = store.recorder(partDir)
        clock.tick(Int.MAX_VALUE.toLong() + 1_000L)
        recorder.recordAlive(File(partDir, METADATA_FILE_NAME))
        assertEquals(openedMs + Int.MAX_VALUE, store.lastAliveMs(partDir))
    }

    @Test
    fun `a torn write is not trusted`() {
        store.recorder(partDir).recordAlive(File(partDir, METADATA_FILE_NAME))
        RandomAccessFile(lastAliveFile, "rw").use { raf ->
            raf.seek(Long.SIZE_BYTES.toLong())
            raf.writeInt(5_000)
            raf.writeInt(4_000)
        }
        assertNull(store.lastAliveMs(partDir))
    }

    @Test
    fun `a file cut short is not trusted`() {
        store.recorder(partDir)
        RandomAccessFile(lastAliveFile, "rw").use { raf -> raf.setLength(Long.SIZE_BYTES + 2L) }
        assertNull(store.lastAliveMs(partDir))
    }

    @Test
    fun `a part with no recorded time has no last alive time`() {
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
