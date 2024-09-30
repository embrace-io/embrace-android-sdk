package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeSchedulingService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.CRASH
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.LOG
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.NETWORK
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.SESSION
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryRunnableComparator
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.worker.PriorityRunnable
import io.embrace.android.embracesdk.internal.worker.PriorityThreadPoolExecutor
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class IntakeServiceImplTest {

    private lateinit var intakeService: IntakeService
    private lateinit var payloadStorageService: FakePayloadStorageService
    private lateinit var schedulingService: FakeSchedulingService
    private lateinit var executorService: BlockableExecutorService
    private lateinit var internalErrorService: FakeInternalErrorService

    private val serializer = TestPlatformSerializer()
    private val sessionEnvelope = Envelope(
        data = SessionPayload(spans = listOf(Span(name = "session-span")))
    )
    private val logEnvelope = Envelope(
        data = LogPayload(logs = listOf(Log(body = "Log data")))
    )
    private val sessionDataExpected = run {
        val baos = ByteArrayOutputStream()
        serializer.toJson(sessionEnvelope, Envelope.sessionEnvelopeType, baos)
        baos.toByteArray()
    }
    private val logDataExpected = run {
        val baos = ByteArrayOutputStream()
        serializer.toJson(logEnvelope, Envelope.logEnvelopeType, baos)
        baos.toByteArray()
    }

    private val clock = FakeClock()
    private val sessionMetadata = StoredTelemetryMetadata.fromEnvelope(clock, SESSION)
    private val logMetadata = StoredTelemetryMetadata.fromEnvelope(clock, LOG)
    private val networkMetadata = StoredTelemetryMetadata.fromEnvelope(clock, NETWORK)
    private val crashMetadata = StoredTelemetryMetadata.fromEnvelope(clock, CRASH)
    private val sessionMetadata2 = StoredTelemetryMetadata.fromEnvelope(clock.apply { tick(100L) }, SESSION)
    private val logMetadata2 = StoredTelemetryMetadata.fromEnvelope(clock.apply { tick(100L) }, LOG)
    private val networkMetadata2 = StoredTelemetryMetadata.fromEnvelope(clock.apply { tick(100L) }, NETWORK)
    private val crashMetadata2 = StoredTelemetryMetadata.fromEnvelope(clock.apply { tick(100L) }, CRASH)

    @Before
    fun setUp() {
        internalErrorService = FakeInternalErrorService()
        payloadStorageService = FakePayloadStorageService()
        schedulingService = FakeSchedulingService()
        executorService = BlockableExecutorService(blockingMode = true)
        intakeService = IntakeServiceImpl(
            schedulingService,
            payloadStorageService,
            internalErrorService,
            serializer,
            PriorityWorker(executorService)
        )
    }

    @Test
    fun `graceful shutdown on crash`() {
        with(intakeService) {
            take(sessionEnvelope, sessionMetadata)
            take(logEnvelope, logMetadata)
            handleCrash("crash-id")
            try {
                take(sessionEnvelope, sessionMetadata)
            } catch (ignored: RejectedExecutionException) {
                // this is an artefact of testing - WorkerThreadModule typically passes in an
                // executor that has a custom rejection handler
            }
        }

        assertEquals(2, payloadStorageService.storedPayloads().size)
        val sessionObj = payloadStorageService.storedPayloads()[0]
        val logObj = payloadStorageService.storedPayloads()[1]
        assertArrayEquals(sessionDataExpected, sessionObj)
        assertArrayEquals(logDataExpected, logObj)

        // assert scheduling service was notified
        assertEquals(2, schedulingService.payloadIntakeCount)
    }

    @Test
    fun `take log`() {
        intakeService.take(logEnvelope, logMetadata)
        executorService.runCurrentlyBlocked()

        // assert filename is valid & contains correct metadata
        val filename = payloadStorageService.storedFilenames().single()
        val metadata = StoredTelemetryMetadata.fromFilename(filename).getOrThrow()
        assertEquals(LOG, metadata.envelopeType)

        // assert payload was stored
        val obj = payloadStorageService.storedPayloads().single()
        assertArrayEquals(logDataExpected, obj)

        // assert scheduling service was notified
        assertEquals(1, schedulingService.payloadIntakeCount)
    }

    @Test
    fun `take session`() {
        intakeService.take(sessionEnvelope, sessionMetadata)
        executorService.runCurrentlyBlocked()

        // assert filename is valid & contains correct metadata
        val filename = payloadStorageService.storedFilenames().single()
        val metadata = StoredTelemetryMetadata.fromFilename(filename).getOrThrow()
        assertEquals(SESSION, metadata.envelopeType)

        // assert payload was stored
        val obj = payloadStorageService.storedPayloads().single()
        assertArrayEquals(sessionDataExpected, obj)

        // assert scheduling service was notified
        assertEquals(1, schedulingService.payloadIntakeCount)
    }

    @Test
    fun `exception in payload storage`() {
        payloadStorageService.failStorage = true
        intakeService.take(logEnvelope, logMetadata)
        executorService.runCurrentlyBlocked()

        // assert nothing was stored but no exception was thrown
        assertEquals(0, payloadStorageService.storedPayloadCount())
        val exc = internalErrorService.throwables.single()
        assertTrue(exc is IOException)
    }

    @Test
    fun `priority ordering`() {
        // construct priority executor & wait with latch until all tasks submitted
        val executor = PriorityThreadPoolExecutor(
            Executors.defaultThreadFactory(),
            { _, _ -> },
            1,
            1,
            storedTelemetryRunnableComparator
        )
        val latch = CountDownLatch(1)
        executor.submit(
            PriorityRunnable(sessionMetadata) {
                latch.await(1000, TimeUnit.MILLISECONDS)
            }
        )
        val worker = PriorityWorker<StoredTelemetryMetadata>(executor)
        intakeService = IntakeServiceImpl(
            schedulingService,
            payloadStorageService,
            internalErrorService,
            TestPlatformSerializer(),
            worker
        )
        with(intakeService) {
            take(sessionEnvelope, sessionMetadata)
            take(logEnvelope, crashMetadata)
            take(logEnvelope, networkMetadata)
            take(logEnvelope, logMetadata)
            clock.tick(1000)
            take(logEnvelope, logMetadata2)
            take(logEnvelope, networkMetadata2)
            take(logEnvelope, crashMetadata2)
            take(sessionEnvelope, sessionMetadata2)

            // stop blocking the executor
            latch.countDown()
        }
        worker.shutdownAndWait(1000)
        assertEquals(8, payloadStorageService.storedPayloadCount())
        assertEquals(8, payloadStorageService.storeCount.get())

        // assert payloads were prioritised in the expected order
        val observedTypes = payloadStorageService.storedFilenames().map {
            val metadata = StoredTelemetryMetadata.fromFilename(it).getOrThrow()
            metadata.envelopeType
        }
        val expected = listOf(CRASH, CRASH, SESSION, SESSION, LOG, LOG, NETWORK, NETWORK)
        assertEquals(expected, observedTypes)
    }
}
