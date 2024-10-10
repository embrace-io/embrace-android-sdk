package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.CRASH
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.LOG
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.NETWORK
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.SESSION
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPInputStream

class PayloadStorageServiceImplTest {

    companion object {
        private const val TIMESTAMP = 1500000000L
        private const val UUID = "uuid"
        private const val PROCESS_ID = "pid"
    }

    private val metadata = StoredTelemetryMetadata(TIMESTAMP, UUID, PROCESS_ID, SESSION)
    private lateinit var service: PayloadStorageService
    private lateinit var outputDir: File
    private lateinit var logger: FakeEmbLogger
    private lateinit var currentProcessId: String

    @Before
    fun setUp() {
        outputDir = Files.createTempDirectory("output").toFile()
        outputDir.deleteRecursively()
        logger = FakeEmbLogger(false)
        currentProcessId = PROCESS_ID
        service = PayloadStorageServiceImpl(lazy { outputDir }, { currentProcessId }, logger)
    }

    @Test
    fun `test store and load object`() {
        val fileContents = "test"

        // store file
        service.store(metadata) {
            it.write(fileContents.toByteArray())
        }
        val files = outputDir.listFiles()
        val file = files?.single() ?: error("File not found")

        // verify file content was gzipped by reading the bytes through a GZIPInputStream
        assertEquals(fileContents, GZIPInputStream(file.inputStream()).bufferedReader().readText())

        // verify file content that is loaded is gzipped
        val observed = GZIPInputStream(service.loadPayloadAsStream(metadata)).bufferedReader().readText()
        assertEquals(fileContents, observed)

        // delete file
        service.delete(metadata)
        assertEquals(0, outputDir.listFiles()?.size)
        assertNull(service.loadPayloadAsStream(metadata))
    }

    @Test
    fun `delete non existent file`() {
        service.delete(metadata) // no exception thrown
        assertNull(outputDir.listFiles())
    }

    @Test
    fun `load non existent file`() {
        assertNull(service.loadPayloadAsStream(metadata))
        assertNotNull(logger.internalErrorMessages.single().throwable)
    }

    @Test
    fun `exception in serialization action when storing`() {
        service.store(metadata) {
            error("Whoops!")
        }
        assertNotNull(logger.internalErrorMessages.single().throwable)
    }

    @Test
    fun `test objects pruned past limit`() {
        assertNull(outputDir.listFiles())
        service = PayloadStorageServiceImpl(lazy { outputDir }, { currentProcessId }, logger, 4)

        // exceed storage limit
        listOf(
            Pair(0L, CRASH),
            Pair(0L, SESSION),
            Pair(0L, LOG),
            Pair(0L, NETWORK),
            Pair(1000L, CRASH),
            Pair(1000L, SESSION),
            Pair(1000L, LOG),
            Pair(1000L, NETWORK)
        ).forEach {
            val metadata = StoredTelemetryMetadata(it.first, UUID, PROCESS_ID, it.second)
            service.store(metadata) { stream ->
                stream.write("test".toByteArray())
            }
        }

        val files = outputDir.listFiles() ?: error("No files found")
        val outputs = files.map { StoredTelemetryMetadata.fromFilename(it.name).getOrThrow() }
            .sortedWith(storedTelemetryComparator)
            .map { Pair(it.timestamp, it.envelopeType) }

        val expected = listOf(
            Pair(0L, CRASH),
            Pair(1000L, CRASH),
            Pair(0L, SESSION),
            Pair(1000L, SESSION)
        )
        assertEquals(expected, outputs)
        val msg = logger.internalErrorMessages.last().throwable?.message
        assertEquals("Pruned payload storage", msg)
    }

    @Test
    fun `getUndeliveredPayloads only returns incomplete ones with a different process identifier than the current`() {
        listOf("old_pid" to 100_000L, PROCESS_ID to 200_000L).forEach { appInstance ->
            listOf(true, false).forEach { complete ->
                listOf(CRASH, SESSION, LOG, NETWORK).forEach { type ->
                    val metadata = StoredTelemetryMetadata(
                        timestamp = appInstance.second,
                        uuid = UUID,
                        processId = appInstance.first,
                        envelopeType = type,
                        complete = complete,
                    )
                    service.store(metadata) { stream ->
                        stream.write("test".toByteArray())
                    }
                }
            }
        }

        with(service.getUndeliveredPayloads()) {
            assertEquals(4, size)
            assertEquals(0, filter { it.complete }.size)
            assertEquals(0, filter { it.processId == PROCESS_ID }.size)
        }
    }

    @Test
    fun `incomplete payload becomes undelivered when process identifier changes`() {
        service.store(metadata.copy(complete = false)) { stream ->
            stream.write("test".toByteArray())
        }

        assertEquals(0, service.getUndeliveredPayloads().size)
        currentProcessId = "new-pid"
        assertEquals(1, service.getUndeliveredPayloads().size)
    }
}
