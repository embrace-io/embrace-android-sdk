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

internal class SessionPartLastWrittenTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var logger: FakeInternalLogger
    private lateinit var target: SessionPartWriteTarget
    private lateinit var service: SessionReconstructionService

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions")
        clock = FakeClock()
        logger = FakeInternalLogger(throwOnInternalError = false)
        target = SessionPartWriteTarget(lazy { sessionsDir }, clock) { partDirectory }
        service = SessionReconstructionService(lazy { sessionsDir }, logger)
        File(sessionsDir, partDirectory.dirName).mkdirs()
    }

    @Test
    fun `each write of a part file moves the part's last write to the clock's time`() {
        SessionMetadataWriter(
            target = target,
            metadataSource = { fullyPopulatedMetadata },
            resourceSource = { fullyPopulatedResource },
            envelopeVersion = "0.1.0",
            envelopeType = "spans",
            sharedLibSymbolMappingSource = { null },
            logger = logger,
        ).write()
        assertEquals(clock.now(), service.lastUpdatedMs(partDirectory))

        clock.tick(5_000)
        CompletedSpansWriter(target, logger).write(listOf(fullyPopulatedSpan))
        assertEquals(clock.now(), service.lastUpdatedMs(partDirectory))

        val snapshots = SpanSnapshotsWriter(target, logger)
        clock.tick(5_000)
        snapshots.write(listOf(inFlightSpan))
        assertEquals(clock.now(), service.lastUpdatedMs(partDirectory))

        clock.tick(5_000)
        snapshots.append(listOf(inFlightSpan.copy(name = "renamed"))) { emptyList() }
        assertEquals(clock.now(), service.lastUpdatedMs(partDirectory))
    }

    @Test
    fun `a part with no files on disk has no last write`() {
        assertNull(service.lastUpdatedMs(partDirectory))
        assertNull(service.lastUpdatedMs(partDirectory.copy(uuid = "d2610cd1-389f-422a-bfbc-25312c7a599a")))
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
