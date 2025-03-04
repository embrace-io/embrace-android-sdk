package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeSchedulingService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.ATTACHMENT
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.BLOB
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.CRASH
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.LOG
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.SESSION
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryRunnableComparator
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

class IntakeServiceImplTest {

    companion object {
        private const val UUID = "uuid"
        private const val PROCESS_ID = "pid"
    }

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
    private val logEnvelope = Envelope(
        data = LogPayload(logs = listOf(Log(body = "Log data")))
    )
    private val attachmentEnvelope = Envelope(
        data = Pair("my-id", ByteArray(2))
    )
    private val sessionDataExpected = run {
        val baos = ByteArrayOutputStream()
        serializer.toJson(sessionEnvelope, Envelope.sessionEnvelopeType, GZIPOutputStream(baos))
        baos.toByteArray()
    }
    private val logDataExpected = run {
        val baos = ByteArrayOutputStream()
        serializer.toJson(logEnvelope, Envelope.logEnvelopeType, GZIPOutputStream(baos))
        baos.toByteArray()
    }

    private val clock = FakeClock()
    private val sessionMetadata = StoredTelemetryMetadata(clock.now(), UUID, PROCESS_ID, SESSION)
    private val attachmentMetadata =
        StoredTelemetryMetadata(clock.now(), UUID, PROCESS_ID, ATTACHMENT, payloadType = PayloadType.ATTACHMENT)
    private val logMetadata = StoredTelemetryMetadata(clock.now(), UUID, PROCESS_ID, LOG, payloadType = PayloadType.LOG)
    private val networkMetadata = StoredTelemetryMetadata(clock.now(), UUID, PROCESS_ID, BLOB)
    private val crashMetadata = StoredTelemetryMetadata(clock.now(), UUID, PROCESS_ID, CRASH)
    private val sessionMetadata2 = StoredTelemetryMetadata(clock.apply { tick(100L) }.now(), UUID, PROCESS_ID, SESSION)
    private val logMetadata2 = StoredTelemetryMetadata(clock.apply { tick(100L) }.now(), UUID, PROCESS_ID, LOG)
    private val attachmentMetadata2 =
        StoredTelemetryMetadata(clock.apply { tick(100L) }.now(), UUID, PROCESS_ID, ATTACHMENT)
    private val networkMetadata2 = StoredTelemetryMetadata(clock.apply { tick(100L) }.now(), UUID, PROCESS_ID, BLOB)
    private val crashMetadata2 = StoredTelemetryMetadata(clock.apply { tick(100L) }.now(), UUID, PROCESS_ID, CRASH)

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
    fun `graceful shutdown on crash`() {
        with(intakeService) {
            take(sessionEnvelope, sessionMetadata)
            take(logEnvelope, logMetadata)
            shutdown()
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
    fun `take attachment`() {
        intakeService.take(attachmentEnvelope, attachmentMetadata)
        executorService.runCurrentlyBlocked()

        // assert filename is valid & contains correct metadata
        val filename = payloadStorageService.storedFilenames().single()
        val metadata = StoredTelemetryMetadata.fromFilename(filename).getOrThrow()
        assertEquals(ATTACHMENT, metadata.envelopeType)

        // assert payload was stored
        val obj = payloadStorageService.storedPayloads().single()
        assertNotNull(obj)

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
    fun `cache session`() {
        intakeService.take(sessionEnvelope, sessionMetadata.copy(complete = false))
        executorService.runCurrentlyBlocked()

        // assert filename is valid & contains correct metadata
        val filename = cacheStorageService.storedFilenames().single()
        val metadata = StoredTelemetryMetadata.fromFilename(filename).getOrThrow()
        assertEquals(SESSION, metadata.envelopeType)

        // assert payload was stored
        val obj = cacheStorageService.storedPayloads().single()
        assertArrayEquals(sessionDataExpected, obj)

        // assert scheduling service was NOT notified
        assertEquals(0, schedulingService.payloadIntakeCount)
    }

    @Test
    fun `multiple cache attempts are ignored`() {
        val cache1 = StoredTelemetryMetadata(clock.now(), UUID, "1", SESSION, complete = false)
        val cache2 = StoredTelemetryMetadata(clock.now(), UUID, "2", SESSION, complete = false)
        val cache3 = StoredTelemetryMetadata(clock.now(), UUID, "3", SESSION, complete = false)
        intakeService.take(sessionEnvelope, cache1)
        intakeService.take(sessionEnvelope, cache2)
        intakeService.take(sessionEnvelope, cache3)
        executorService.runCurrentlyBlocked()

        // only one file was stored and it's the most recent one
        val filename = cacheStorageService.storedFilenames().single()
        val metadata = StoredTelemetryMetadata.fromFilename(filename).getOrThrow()
        assertEquals(SESSION, metadata.envelopeType)
        assertEquals("3", metadata.processId)
    }

    @Test
    fun `exception in payload storage`() {
        payloadStorageService.failStorage = true
        intakeService.take(logEnvelope, logMetadata)
        executorService.runCurrentlyBlocked()

        // assert nothing was stored but no exception was thrown
        assertEquals(0, payloadStorageService.storedPayloadCount())
        val throwable = logger.internalErrorMessages.single().throwable
        assertTrue(throwable is IOException)
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
            cacheStorageService,
            logger,
            TestPlatformSerializer(),
            worker
        )
        with(intakeService) {
            take(sessionEnvelope, sessionMetadata)
            take(logEnvelope, crashMetadata)
            take(logEnvelope, networkMetadata)
            take(attachmentEnvelope, attachmentMetadata)
            take(logEnvelope, logMetadata)
            clock.tick(1000)
            take(attachmentEnvelope, attachmentMetadata2)
            take(logEnvelope, logMetadata2)
            take(logEnvelope, networkMetadata2)
            take(logEnvelope, crashMetadata2)
            take(sessionEnvelope, sessionMetadata2)

            // stop blocking the executor
            latch.countDown()
        }
        worker.shutdownAndWait(1000)
        assertEquals(10, payloadStorageService.storedPayloadCount())
        assertEquals(10, payloadStorageService.storeCount.get())

        // assert payloads were prioritised in the expected order
        val observedTypes = payloadStorageService.storedFilenames().map {
            val metadata = StoredTelemetryMetadata.fromFilename(it).getOrThrow()
            metadata.envelopeType
        }
        val expected = listOf(CRASH, CRASH, SESSION, SESSION, ATTACHMENT, ATTACHMENT, LOG, LOG, BLOB, BLOB)
        assertEquals(expected, observedTypes)
    }

    @Test
    fun `only cached session payloads cleaned up and only by another session payload intake`() {
        executorService.blockingMode = false
        val cache1 = StoredTelemetryMetadata(clock.now(), UUID, PROCESS_ID, SESSION, complete = false).apply {
            intakeService.take(
                intake = sessionEnvelope,
                metadata = this
            )
        }

        intakeService.take(
            intake = logEnvelope,
            metadata = StoredTelemetryMetadata(clock.tick(), UUID, PROCESS_ID, LOG, complete = true)
        )

        assertTrue(cacheStorageService.storedFilenames().contains(cache1.filename))

        val cache2 = StoredTelemetryMetadata(clock.tick(), UUID, PROCESS_ID, SESSION, complete = false).apply {
            intakeService.take(
                intake = sessionEnvelope,
                metadata = this
            )
        }

        assertFalse(cacheStorageService.storedFilenames().contains(cache1.filename))
        assertTrue(cacheStorageService.storedFilenames().contains(cache2.filename))

        val session = StoredTelemetryMetadata(clock.tick(), UUID, PROCESS_ID, SESSION, complete = true).apply {
            intakeService.take(
                intake = sessionEnvelope,
                metadata = this
            )
        }

        assertFalse(cacheStorageService.storedFilenames().contains(cache2.filename))
        assertTrue(payloadStorageService.storedFilenames().contains(session.filename))
        assertTrue(logger.internalErrorMessages.isEmpty())
    }

    @Test
    fun `new empty crash envelope caching will remove the old copy`() {
        executorService.blockingMode = false
        val cache1 =
            StoredTelemetryMetadata(clock.now(), UUID, PROCESS_ID, CRASH, false, PayloadType.UNKNOWN).apply {
                intakeService.take(
                    intake = logEnvelope,
                    metadata = this
                )
            }

        assertEquals(cache1.filename, cacheStorageService.storedFilenames().single())

        val cache2 =
            StoredTelemetryMetadata(clock.tick(), UUID, PROCESS_ID, CRASH, false, PayloadType.UNKNOWN).apply {
                intakeService.take(
                    intake = logEnvelope,
                    metadata = this
                )
            }

        assertEquals(cache2.filename, cacheStorageService.storedFilenames().single())
        assertTrue(logger.internalErrorMessages.isEmpty())
    }

    @Test
    fun `intake of cache request for unexpected envelope type will succeed but produce internal error`() {
        executorService.blockingMode = false
        val cache1 =
            StoredTelemetryMetadata(clock.now(), UUID, PROCESS_ID, BLOB, false, PayloadType.AEI).apply {
                intakeService.take(
                    intake = logEnvelope,
                    metadata = this
                )
            }

        assertEquals(cache1.filename, cacheStorageService.storedFilenames().single())
        assertEquals(InternalErrorType.INTAKE_UNEXPECTED_TYPE.toString(), logger.internalErrorMessages.single().msg)
    }
}
