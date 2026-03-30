package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileStorageServiceImplTest {

    private companion object {
        private const val DUMMY_CONTENT = "my file contents"
    }

    private lateinit var outputDir: File
    private lateinit var service: FileStorageService
    private lateinit var logger: FakeInternalLogger
    private lateinit var executor: BlockingScheduledExecutorService

    @Before
    fun setUp() {
        outputDir = Files.createTempDirectory("temp").toFile().apply {
            mkdirs()
        }
        logger = FakeInternalLogger(throwOnInternalError = false)
        executor = BlockingScheduledExecutorService()
        service = FileStorageServiceImpl(
            lazy { outputDir },
            PriorityWorker(executor),
            logger
        )
    }

    @Test
    fun `load payload stream`() {
        storeDummyFile(fakeSessionStoredTelemetryMetadata)

        val storedPayload = service.getStoredPayloads().single()
        assertEquals(fakeSessionStoredTelemetryMetadata.filename, storedPayload.filename)

        service.loadPayloadAsStream(fakeSessionStoredTelemetryMetadata)?.use {
            assertEquals(DUMMY_CONTENT, it.bufferedReader().readText())
        }

        service.delete(fakeSessionStoredTelemetryMetadata)
        executor.queueCompletionTask()
        assertNull(service.loadPayloadAsStream(fakeSessionStoredTelemetryMetadata))
    }

    @Test
    fun `load payload stream error`() {
        assertNull(service.loadPayloadAsStream(fakeSessionStoredTelemetryMetadata))
        checkNotNull(logger.internalErrorMessages.single())
    }

    @Test
    fun `storedFiles entry removed even when file already deleted from disk`() {
        storeDummyFile(fakeSessionStoredTelemetryMetadata)
        assertEquals(1, service.getStoredPayloads().size)

        // Delete actual file but out of sight this service
        assertTrue(File(outputDir, fakeSessionStoredTelemetryMetadata.filename).delete())

        // File is gone but storedFiles still has the entry
        assertEquals(1, service.getStoredPayloads().size)

        // service.delete should clean up storedFiles regardless of File.delete() result
        var callbackFired = false
        service.delete(fakeSessionStoredTelemetryMetadata) {
            callbackFired = true
        }
        executor.queueCompletionTask()

        // verify that the payload is gone and the callback is fired even if the actual file delete didn't happen
        assertEquals(0, service.getStoredPayloads().size)
        assertTrue(callbackFired)
    }

    private fun storeDummyFile(metadata: StoredTelemetryMetadata) {
        service.store(metadata) {
            it.write(DUMMY_CONTENT.toByteArray())
        }
    }

    val fakeSessionStoredTelemetryMetadata = StoredTelemetryMetadata(
        timestamp = DEFAULT_FAKE_CURRENT_TIME + 1000L,
        uuid = "30690ad1-6b87-4e08-b72c-7deca14451d8",
        processIdentifier = "8115ec91-3e5e-4d8a-816d-cc40306f9822",
        envelopeType = SupportedEnvelopeType.SESSION,
        true,
        payloadType = PayloadType.SESSION,
    )
}
