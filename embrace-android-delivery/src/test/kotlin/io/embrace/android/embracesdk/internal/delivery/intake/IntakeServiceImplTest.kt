package io.embrace.android.embracesdk.internal.delivery.intake

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
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
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
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
    private lateinit var logger: FakeInternalLogger

    private val serializer = TestPlatformSerializer()
    private val sessionEnvelope = Envelope(
        data = SessionPartPayload(spans = listOf(Span(name = "session-span")))
    )
    private val logEnvelope = Envelope(
        data = LogPayload(logs = listOf(Log(body = "Log data")))
    )
    private val attachmentEnvelope = Envelope(
        data = Pair("my-id", ByteArray(2))
    )
    private val sessionDataExpected = run {
        val baos = ByteArrayOutputStream()
        serializer.toJson(sessionEnvelope, Envelope.sessionEnvelopeSerializer, GZIPOutputStream(baos))
        baos.toByteArray()
    }
    private val logDataExpected = run {
        val baos = ByteArrayOutputStream()
        serializer.toJson(logEnvelope, Envelope.logEnvelopeSerializer, GZIPOutputStream(baos))
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
        logger = FakeInternalLogger(false)
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
        assertEquals("3", metadata.processIdentifier)
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
    fun `previous cached session snapshot is cleaned up automatically by IntakeServiceImpl`() {
        executorService.blockingMode = false
        val snapshot1 = StoredTelemetryMetadata(
            timestamp = clock.now(),
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = SESSION,
            complete = false
        ).apply {
            intakeService.take(intake = sessionEnvelope, metadata = this)
        }

        // Log payload intake doesn't affect session cache
        intakeService.take(
            intake = logEnvelope,
            metadata = StoredTelemetryMetadata(
                timestamp = clock.tick(),
                uuid = UUID,
                processIdentifier = PROCESS_ID,
                envelopeType = LOG,
                complete = true
            )
        )
        assertTrue(cacheStorageService.storedFilenames().contains(snapshot1.filename))

        // New snapshot replaces old one
        val snapshot2 = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = SESSION,
            complete = false
        ).apply {
            intakeService.take(intake = sessionEnvelope, metadata = this)
        }
        assertFalse(cacheStorageService.storedFilenames().contains(snapshot1.filename))
        assertTrue(cacheStorageService.storedFilenames().contains(snapshot2.filename))

        // Complete session payload cleans up snapshot
        val session = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = SESSION,
            complete = true
        ).apply {
            intakeService.take(intake = sessionEnvelope, metadata = this)
        }
        assertFalse(cacheStorageService.storedFilenames().contains(snapshot2.filename))
        assertTrue(payloadStorageService.storedFilenames().contains(session.filename))
        assertTrue(logger.internalErrorMessages.isEmpty())
    }

    @Test
    fun `new empty crash envelope caching will remove the old copy automatically`() {
        executorService.blockingMode = false
        val snapshot1 = StoredTelemetryMetadata(
            timestamp = clock.now(),
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = CRASH,
            complete = false,
            payloadType = PayloadType.UNKNOWN
        ).apply {
            intakeService.take(intake = logEnvelope, metadata = this)
        }

        assertEquals(snapshot1.filename, cacheStorageService.storedFilenames().single())

        val snapshot2 = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = CRASH,
            complete = false,
            payloadType = PayloadType.UNKNOWN
        ).apply {
            intakeService.take(intake = logEnvelope, metadata = this)
        }

        assertEquals(snapshot2.filename, cacheStorageService.storedFilenames().single())
        assertTrue(logger.internalErrorMessages.isEmpty())
    }

    @Test
    fun `explicit staleEntry takes precedence over internal cache tracking`() {
        executorService.blockingMode = false

        // Cache a session snapshot — tracked internally by IntakeServiceImpl
        val snapshot = StoredTelemetryMetadata(
            timestamp = clock.now(),
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = SESSION,
            complete = false
        ).apply {
            intakeService.take(intake = sessionEnvelope, metadata = this)
        }
        assertEquals(1, cacheStorageService.storedPayloadCount())

        // Resurrection provides an explicit staleEntry for a different metadata.
        // The explicit staleEntry should be deleted, NOT the internally tracked snapshot.
        val resurrectionSource = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = "other-uuid",
            processIdentifier = "old-pid",
            envelopeType = SESSION,
            complete = false
        ).apply {
            cacheStorageService.store(this) {
                it.write("old".toByteArray())
            }
        }
        assertEquals(2, cacheStorageService.storedPayloadCount())

        intakeService.take(
            intake = sessionEnvelope,
            metadata = StoredTelemetryMetadata(
                timestamp = clock.tick(),
                uuid = UUID,
                processIdentifier = PROCESS_ID,
                envelopeType = SESSION,
                complete = true
            ),
            staleEntry = resurrectionSource
        )

        // resurrectionSource was deleted (explicit staleEntry), snapshot is still present
        assertFalse(cacheStorageService.storedFilenames().contains(resurrectionSource.filename))
        assertTrue(cacheStorageService.storedFilenames().contains(snapshot.filename))
        assertEquals(1, payloadStorageService.storedPayloadCount())
    }

    @Test
    fun `complete intake with no prior cached or not cacheable payloads deletes nothing`() {
        executorService.blockingMode = false

        intakeService.take(intake = sessionEnvelope, metadata = sessionMetadata)
        intakeService.take(intake = logEnvelope, metadata = logMetadata)

        assertEquals(2, payloadStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.deleteCount.get())
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
        assertEquals(InternalErrorType.IntakeUnexpectedType.toString(), logger.internalErrorMessages.single().msg)
    }

    @Test
    fun `take returns a future that resolves after storage`() {
        val future = intakeService.take(sessionEnvelope, sessionMetadata)
        assertFalse(future.isDone)
        assertEquals(0, payloadStorageService.storedPayloadCount())
        executorService.runCurrentlyBlocked()
        assertTrue(future.isDone)
        assertEquals(1, payloadStorageService.storedPayloadCount())
        assertEquals(1, schedulingService.payloadIntakeCount)
    }

    @Test
    fun `JVM crash intake shuts scheduling down and persists sync`() {
        val jvmCrashMetadata = StoredTelemetryMetadata(
            timestamp = clock.now(),
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = CRASH,
            complete = true,
            payloadType = PayloadType.JVM_CRASH
        )

        intakeService.take(logEnvelope, jvmCrashMetadata)
        assertEquals(1, schedulingService.shutdownCount)
        assertEquals(0, schedulingService.payloadIntakeCount)

        // crash was persisted to delivery storage, ready for next launch
        val filename = payloadStorageService.storedFilenames().single()
        val metadata = StoredTelemetryMetadata.fromFilename(filename).getOrThrow()
        assertEquals(CRASH, metadata.envelopeType)
        assertEquals(PayloadType.JVM_CRASH, metadata.payloadType)
    }

    @Test
    fun `JVM crash intake does not trigger for incomplete envelope`() {
        val incompleteCrashMetadata = StoredTelemetryMetadata(
            timestamp = clock.now(),
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = CRASH,
            complete = false,
            payloadType = PayloadType.JVM_CRASH
        )

        intakeService.take(logEnvelope, incompleteCrashMetadata)

        assertEquals(0, schedulingService.shutdownCount)
        assertEquals(1, executorService.tasksBlockedCount())
        assertEquals(0, payloadStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.storedPayloadCount())
    }

    @Test
    fun `native crash intake is not handled synchronously`() {
        val nativeCrashMetadata = StoredTelemetryMetadata(
            timestamp = clock.now(),
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = CRASH,
            complete = true,
            payloadType = PayloadType.NATIVE_CRASH
        )

        intakeService.take(logEnvelope, nativeCrashMetadata)

        assertEquals(0, schedulingService.shutdownCount)
        assertEquals(1, executorService.tasksBlockedCount())
        assertEquals(0, payloadStorageService.storedPayloadCount())

        executorService.runCurrentlyBlocked()
        assertEquals(1, payloadStorageService.storedPayloadCount())
        assertEquals(1, schedulingService.payloadIntakeCount)
    }

    @Test
    fun `React Native crash intake persists sync`() {
        val rnCrashMetadata = StoredTelemetryMetadata(
            timestamp = clock.now(),
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = CRASH,
            complete = true,
            payloadType = PayloadType.REACT_NATIVE_CRASH
        )

        intakeService.take(logEnvelope, rnCrashMetadata)
        assertEquals(1, schedulingService.shutdownCount)
        assertEquals(0, schedulingService.payloadIntakeCount)

        val filename = payloadStorageService.storedFilenames().single()
        val metadata = StoredTelemetryMetadata.fromFilename(filename).getOrThrow()
        assertEquals(CRASH, metadata.envelopeType)
        assertEquals(PayloadType.REACT_NATIVE_CRASH, metadata.payloadType)
    }

    @Test
    fun `JVM crash intake deletes the staleEntry synchronously`() {
        val stale = StoredTelemetryMetadata(
            timestamp = clock.now(),
            uuid = "stale-uuid",
            processIdentifier = PROCESS_ID,
            envelopeType = CRASH,
            complete = false,
            payloadType = PayloadType.UNKNOWN
        ).apply {
            cacheStorageService.store(this) { it.write("stale".toByteArray()) }
        }
        assertEquals(1, cacheStorageService.storedPayloadCount())

        val jvmCrashMetadata = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = CRASH,
            complete = true,
            payloadType = PayloadType.JVM_CRASH
        )

        intakeService.take(logEnvelope, jvmCrashMetadata, staleEntry = stale)

        assertEquals(1, schedulingService.shutdownCount)
        assertEquals(1, payloadStorageService.storedPayloadCount())
        assertFalse(cacheStorageService.storedFilenames().contains(stale.filename))
    }

    @Test
    fun `crash teardown routes subsequent session intake through synchronous path`() {
        // create snapshot from periodic caching
        val snapshotMetadata = sessionMetadata.copy(
            timestamp = clock.tick(),
            uuid = "snapshot-uuid",
            complete = false
        )
        intakeService.take(sessionEnvelope, snapshotMetadata)
        executorService.runCurrentlyBlocked()
        assertEquals(1, cacheStorageService.storedPayloadCount())

        // submit JVM crash payload
        val jvmCrashMetadata = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = "crash-uuid",
            processIdentifier = PROCESS_ID,
            envelopeType = CRASH,
            complete = true,
            payloadType = PayloadType.JVM_CRASH
        )
        intakeService.take(logEnvelope, jvmCrashMetadata)

        // submit final session payload after crash
        val crashSessionMetadata = sessionMetadata.copy(timestamp = clock.tick(), uuid = "crash-session")
        intakeService.take(sessionEnvelope, crashSessionMetadata)

        // both crash payload and crash session are persisted synchronously, snapshot file is deleted
        assertEquals(2, payloadStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.storedPayloadCount())

        // subsequent session snapshot rejected
        assertIntakeRejected(
            sessionEnvelope,
            sessionMetadata.copy(
                timestamp = clock.tick(),
                uuid = "late-snapshot",
                complete = false
            )
        )

        // subsequent log rejected
        assertIntakeRejected(
            logEnvelope,
            StoredTelemetryMetadata(
                timestamp = clock.tick(),
                uuid = "log-uuid",
                processIdentifier = PROCESS_ID,
                envelopeType = LOG,
                complete = true,
                payloadType = PayloadType.LOG
            )
        )

        // subsequent crash rejected
        assertIntakeRejected(
            logEnvelope,
            StoredTelemetryMetadata(
                timestamp = clock.tick(),
                uuid = "crash-uuid",
                processIdentifier = PROCESS_ID,
                envelopeType = CRASH,
                complete = true,
                payloadType = PayloadType.JVM_CRASH
            )
        )
    }

    @Test
    fun `resurrected session part from another process should not seal the intake service`() {
        // crash occurs in the current process, moving intake into CRASH_RECEIVED
        // it is persisted synchronously, by passing the background worker that may not run before the process dies
        val jvmCrashMetadata = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = "crash-uuid",
            processIdentifier = PROCESS_ID,
            envelopeType = CRASH,
            complete = true,
            payloadType = PayloadType.JVM_CRASH
        )
        intakeService.take(logEnvelope, jvmCrashMetadata)
        assertEquals(1, payloadStorageService.storedPayloadCount())
        assertEquals(0, executorService.tasksBlockedCount())

        // a complete SESSION resurrected from a different process arrives during teardown
        // it is persisted synchronously but doesn't seal the intake service
        val resurrectedSessionMetadata = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = "resurrected-session",
            processIdentifier = "old-pid",
            envelopeType = SESSION,
            complete = true,
        )
        intakeService.take(sessionEnvelope, resurrectedSessionMetadata)
        assertEquals(2, payloadStorageService.storedPayloadCount())
        assertEquals(0, executorService.tasksBlockedCount())

        // because the resurrected session did not seal, the crashing process's own session part will be persisted and seals the service
        val crashSessionMetadata = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = "crash-session",
            processIdentifier = PROCESS_ID,
            envelopeType = SESSION,
            complete = true,
        )
        intakeService.take(sessionEnvelope, crashSessionMetadata)
        assertEquals(3, payloadStorageService.storedPayloadCount())
        assertEquals(0, executorService.tasksBlockedCount())

        // the crash session sealed the intake, so a subsequent payload is dropped
        assertIntakeRejected(
            logEnvelope,
            StoredTelemetryMetadata(
                timestamp = clock.tick(),
                uuid = "post-seal-log",
                processIdentifier = PROCESS_ID,
                envelopeType = LOG,
                complete = true,
                payloadType = PayloadType.LOG
            )
        )
    }

    @Test
    fun `all payloads arriving during crash teardown before sealing are persisted, not dropped`() {
        val jvmCrashMetadata = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = "crash-uuid",
            processIdentifier = PROCESS_ID,
            envelopeType = CRASH,
            complete = true,
            payloadType = PayloadType.JVM_CRASH
        )
        intakeService.take(logEnvelope, jvmCrashMetadata)
        assertEquals(1, payloadStorageService.storedPayloadCount())

        val logInWindow = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = "log-in-window",
            processIdentifier = PROCESS_ID,
            envelopeType = LOG,
            complete = true,
            payloadType = PayloadType.LOG
        )
        val attachmentInWindow = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = "attachment-in-window",
            processIdentifier = PROCESS_ID,
            envelopeType = ATTACHMENT,
            complete = true,
            payloadType = PayloadType.ATTACHMENT
        )
        intakeService.take(logEnvelope, logInWindow)
        intakeService.take(attachmentEnvelope, attachmentInWindow)
        assertEquals(3, payloadStorageService.storedPayloadCount())
        assertEquals(0, executorService.tasksBlockedCount())

        // the crashing process's own session part seals the intake (persisted synchronously)
        val crashSessionMetadata = StoredTelemetryMetadata(
            timestamp = clock.tick(),
            uuid = "crash-session",
            processIdentifier = PROCESS_ID,
            envelopeType = SESSION,
            complete = true
        )
        intakeService.take(sessionEnvelope, crashSessionMetadata)
        assertEquals(4, payloadStorageService.storedPayloadCount())
    }

    @Test
    fun `duplicate cache takes will result in the first future being canceled`() {
        val f1 = intakeService.take(sessionEnvelope, sessionMetadata.copy(complete = false))
        assertFalse(f1.isDone)
        assertFalse(f1.isCancelled)
        val f2 = intakeService.take(sessionEnvelope, sessionMetadata.copy(complete = false))
        assertTrue(f1.isCancelled)
        assertTrue(f1.isDone)
        executorService.runCurrentlyBlocked()
        assertEquals(1, cacheStorageService.storedPayloadCount())
        assertFalse(f2.isCancelled)
        assertTrue(f2.isDone)
    }

    private fun assertIntakeRejected(envelope: Envelope<*>, metadata: StoredTelemetryMetadata) {
        val service = when {
            metadata.complete -> payloadStorageService
            else -> cacheStorageService
        }
        service.clearStorage()
        try {
            intakeService.take(envelope, metadata)
        } catch (ignored: RejectedExecutionException) {
            // expected: worker is shut down (the production rejection handler would log and drop)
        }
        assertEquals(0, service.storedPayloadCount())
    }
}
