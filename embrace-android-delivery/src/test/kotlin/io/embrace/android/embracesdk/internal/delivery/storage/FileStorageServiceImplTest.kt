package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private lateinit var logger: FakeEmbLogger
    private lateinit var executor: BlockingScheduledExecutorService

    @Before
    fun setUp() {
        outputDir = Files.createTempDirectory("temp").toFile().apply {
            mkdirs()
        }
        logger = FakeEmbLogger(throwOnInternalError = false)
        executor = BlockingScheduledExecutorService()
        service = FileStorageServiceImpl(
            lazy { outputDir },
            PriorityWorker(executor),
            logger
        )
    }

    @Test
    fun `load payload stream`() {
        service.store(fakeSessionStoredTelemetryMetadata) {
            it.write(DUMMY_CONTENT.toByteArray())
        }

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
}
