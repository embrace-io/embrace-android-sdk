package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.fakeEnvelopeMetadata
import io.embrace.android.embracesdk.fakes.fakeEnvelopeResource
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore.Companion.createNativeCrashEnvelopeMetadata
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CachedLogEnvelopeStoreImplTest {

    private lateinit var outputDir: File
    private lateinit var store: CachedLogEnvelopeStoreImpl
    private lateinit var logger: FakeEmbLogger
    private lateinit var serializer: PlatformSerializer
    private lateinit var executor: BlockingScheduledExecutorService

    @Before
    fun setUp() {
        outputDir = Files.createTempDirectory("temp").toFile().apply {
            mkdirs()
        }
        logger = FakeEmbLogger()
        serializer = TestPlatformSerializer()
        executor = BlockingScheduledExecutorService()

        store = CachedLogEnvelopeStoreImpl(
            outputDir = lazy { outputDir },
            worker = PriorityWorker(executor),
            logger = logger,
            serializer = serializer
        )
    }

    @Test
    fun `payload can be written, retrieved, and deleted`() {
        store.create(
            storedTelemetryMetadata = fakeNativeCrashEnvelope,
            resource = fakeEnvelopeResource,
            metadata = fakeEnvelopeMetadata
        )

        val anotherCrashMetadata = createNativeCrashEnvelopeMetadata(
            sessionId = "another-session",
            processIdentifier = "another-process"
        )

        assertNull(store.get(anotherCrashMetadata))
        val envelope = checkNotNull(store.get(fakeNativeCrashEnvelope))
        with(envelope) {
            assertEquals(fakeEnvelopeResource, resource)
            assertEquals(fakeEnvelopeMetadata, metadata)
        }

        store.clear()
        executor.queueCompletionTask()
        assertNull(store.get(fakeNativeCrashEnvelope))
    }

    companion object {
        val fakeNativeCrashEnvelope = createNativeCrashEnvelopeMetadata(
            sessionId = "old-session-id",
            processIdentifier = "old-process-id"
        )
    }
}
