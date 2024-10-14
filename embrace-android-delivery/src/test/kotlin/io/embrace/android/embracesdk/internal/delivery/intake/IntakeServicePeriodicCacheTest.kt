package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeSchedulingService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.SESSION
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IntakeServicePeriodicCacheTest {

    private lateinit var intakeService: IntakeService
    private lateinit var payloadStorageService: FakePayloadStorageService
    private lateinit var cacheStorageService: FakePayloadStorageService
    private lateinit var schedulingService: FakeSchedulingService
    private lateinit var executorService: BlockableExecutorService
    private lateinit var logger: FakeEmbLogger

    private val serializer = TestPlatformSerializer()
    private val sessionEnvelope = Envelope(
        data = SessionPayload(spans = listOf(Span(name = "session-span")))
    )
    private val clock = FakeClock()

    @Before
    fun setUp() {
        payloadStorageService = FakePayloadStorageService()
        cacheStorageService = FakePayloadStorageService()
        schedulingService = FakeSchedulingService()
        executorService = BlockableExecutorService(blockingMode = true)
        logger = FakeEmbLogger(false)
        intakeService = IntakeServiceImpl(
            schedulingService,
            payloadStorageService,
            cacheStorageService,
            logger,
            serializer,
            PriorityWorker(executorService)
        )
    }

    @Test
    fun `multiple cache attempts are ignored`() {
        // first cache entry was written
        intakeService.take(sessionEnvelope, createSnapshotMetadata("1"))
        executorService.runCurrentlyBlocked()
        assertEquals("1", getMetadata().uuid)
        assertStorageAttempts(1, 0)

        // second cache entry overwrote the first
        clock.tick(2000)
        intakeService.take(sessionEnvelope, createSnapshotMetadata("2"))
        executorService.runCurrentlyBlocked()
        assertEquals("2", getMetadata().uuid)
        assertStorageAttempts(2, 1)

        // third cache entry overwrote the second
        clock.tick(2000)
        intakeService.take(sessionEnvelope, createSnapshotMetadata("3"))
        executorService.runCurrentlyBlocked()
        assertEquals("3", getMetadata().uuid)
        assertStorageAttempts(3, 2)
        assertEquals(0, payloadStorageService.storeCount.get())
        assertEquals(0, payloadStorageService.deleteCount.get())

        // completing a session deleted the cache entry
        clock.tick(2000)
        intakeService.take(sessionEnvelope, createSnapshotMetadata("4").copy(complete = true))
        executorService.runCurrentlyBlocked()
        assertStorageAttempts(3, 3)
        assertEquals(1, payloadStorageService.storeCount.get())
        assertEquals(0, payloadStorageService.deleteCount.get())
        val filename = payloadStorageService.storedFilenames().single()
        val metadata = StoredTelemetryMetadata.fromFilename(filename).getOrThrow()
        assertEquals("4", metadata.uuid)
    }

    private fun assertStorageAttempts(cacheStoreCount: Int, cacheDeleteCount: Int) {
        assertEquals(cacheStoreCount, cacheStorageService.storeCount.get())
        assertEquals(cacheDeleteCount, cacheStorageService.deleteCount.get())
        assertEquals(cacheStoreCount - cacheDeleteCount, cacheStorageService.storedFilenames().size)
    }

    private fun getMetadata(): StoredTelemetryMetadata {
        val filename = cacheStorageService.storedFilenames().single()
        return StoredTelemetryMetadata.fromFilename(filename).getOrThrow()
    }

    private fun createSnapshotMetadata(uuid: String) =
        StoredTelemetryMetadata(clock.now(), uuid, "1", SESSION, complete = false)
}
