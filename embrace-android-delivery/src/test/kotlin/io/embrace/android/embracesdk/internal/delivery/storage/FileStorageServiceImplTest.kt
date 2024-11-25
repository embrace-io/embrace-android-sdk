package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
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

    @Before
    fun setUp() {
        outputDir = Files.createTempDirectory("temp").toFile().apply {
            mkdirs()
        }
        logger = FakeEmbLogger(throwOnInternalError = false)
        service = FileStorageServiceImpl(
            lazy { outputDir },
            logger
        )
    }

    @Test
    fun `load payload stream`() {
        writePayload(fakeSessionStoredTelemetryMetadata)
        val stream = checkNotNull(service.loadPayloadAsStream(fakeSessionStoredTelemetryMetadata))
        assertEquals(DUMMY_CONTENT, stream.bufferedReader().readText())
    }

    @Test
    fun `load payload stream error`() {
        assertNull(service.loadPayloadAsStream(fakeSessionStoredTelemetryMetadata))
        checkNotNull(logger.internalErrorMessages.single())
    }

    private fun writePayload(metadata: StoredTelemetryMetadata) {
        File(outputDir, metadata.filename).writeText(DUMMY_CONTENT)
    }
}
