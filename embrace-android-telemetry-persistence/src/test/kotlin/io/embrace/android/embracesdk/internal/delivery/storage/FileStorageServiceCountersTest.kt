package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeSectionRecorder
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.utils.FileWriteCounters
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * What the single-file storage mechanism puts on disk is published as system trace counters, so
 * that a capture shows what a session cost to persist this way.
 */
internal class FileStorageServiceCountersTest {

    private companion object {
        private const val BYTES_COUNTER = "bytes-written"
        private const val FILES_COUNTER = "files-written"
        private const val CONTENT = "my file contents"
    }

    private lateinit var outputDir: File
    private lateinit var recorder: FakeSectionRecorder
    private lateinit var service: FileStorageService

    private val clock = FakeClock()

    @Before
    fun setUp() {
        outputDir = Files.createTempDirectory("temp").toFile().apply { mkdirs() }
        recorder = FakeSectionRecorder()
        SystemTrace.recorder = recorder
        service = FileStorageServiceImpl(
            lazy { outputDir },
            PriorityWorker(BlockingScheduledExecutorService()),
            FakeInternalLogger(throwOnInternalError = false),
            clock,
            counters = FileWriteCounters(BYTES_COUNTER, FILES_COUNTER),
        )
    }

    @After
    fun tearDown() {
        SystemTrace.recorder = null
    }

    @Test
    fun `nothing is counted before anything is stored`() {
        assertNull(recorder.latestCounter(BYTES_COUNTER))
        assertNull(recorder.latestCounter(FILES_COUNTER))
    }

    @Test
    fun `a stored payload counts the bytes that reached disk`() {
        store(metadata("aaaaaaaa-0000-0000-0000-000000000001"))

        assertEquals(CONTENT.length.toLong(), recorder.latestCounter(BYTES_COUNTER))
        assertEquals(1L, recorder.latestCounter(FILES_COUNTER))
    }

    @Test
    fun `the counts accumulate over the payloads stored`() {
        store(metadata("aaaaaaaa-0000-0000-0000-000000000001"))
        store(metadata("aaaaaaaa-0000-0000-0000-000000000002"))

        assertEquals(2L * CONTENT.length, recorder.latestCounter(BYTES_COUNTER))
        assertEquals(2L, recorder.latestCounter(FILES_COUNTER))
    }

    @Test
    fun `a payload that fails to serialize is not counted`() {
        service.store(metadata("aaaaaaaa-0000-0000-0000-000000000001")) { error("boom") }

        assertNull(recorder.latestCounter(FILES_COUNTER))
    }

    private fun store(metadata: StoredTelemetryMetadata) {
        service.store(metadata) { stream -> stream.write(CONTENT.toByteArray()) }
    }

    private fun metadata(uuid: String) = StoredTelemetryMetadata(
        timestamp = clock.now(),
        uuid = uuid,
        processIdentifier = "proc1",
        envelopeType = SupportedEnvelopeType.SESSION,
        complete = true,
        payloadType = PayloadType.SESSION,
    )
}
