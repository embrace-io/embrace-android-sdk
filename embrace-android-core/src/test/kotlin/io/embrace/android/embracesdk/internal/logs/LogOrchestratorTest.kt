package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeLogEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakePayloadStore
import io.embrace.android.embracesdk.fixtures.deferredLog
import io.embrace.android.embracesdk.fixtures.sendImmediatelyLog
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSourceImpl
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class LogOrchestratorTest {

    companion object {
        private const val NOW = 123L
    }

    private lateinit var logOrchestrator: LogOrchestrator
    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var worker: BackgroundWorker
    private lateinit var logSink: LogSink
    private lateinit var store: FakePayloadStore
    private lateinit var errorHandler: FakeErrorHandler
    private var logStoreFailures = 0
    private val clock = FakeClock()

    @Before
    fun setUp() {
        executorService = BlockingScheduledExecutorService()
        worker =
            BackgroundWorker(executorService)
        logSink = LogSinkImpl()
        store = FakePayloadStore()
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        clock.setCurrentTime(NOW)
        val failingStore = object : PayloadStore by store {
            override fun storeLogPayload(envelope: Envelope<LogPayload>) {
                if (logStoreFailures > 0) {
                    logStoreFailures--
                    error("store failed")
                }
                store.storeLogPayload(envelope)
            }
        }
        logOrchestrator = LogOrchestratorImpl(
            worker,
            clock,
            logSink,
            failingStore,
            FakeLogEnvelopeSource(logPayloadSource = LogPayloadSourceImpl(logSink)),
            errorHandler,
        )
        logSink.registerLogStoredCallback(logOrchestrator::onLogsAdded)
    }

    @Test
    fun `send a batch of logs when the batch max size is reached`() {
        val logs = mutableListOf<Log>()

        // Fill the sink with max batch size - 1 logs
        repeat(LogOrchestratorImpl.MAX_LOGS_PER_BATCH - 1) {
            logs.add(Log())
        }
        logSink.storeLogs(logs.toList())

        // Verify the logs are not sent
        assertEquals(LogOrchestratorImpl.MAX_LOGS_PER_BATCH - 1, logSink.logsForNextBatch().size)
        verifyPayloadNotSent()

        // Add one more log to reach max batch size
        logSink.storeLogs(listOf(Log()))

        // Verify the logs are sent
        assertTrue(logSink.logsForNextBatch().isEmpty())
        verifyPayload(LogOrchestratorImpl.MAX_LOGS_PER_BATCH)
    }

    @Test
    fun `logs are sent after inactivity time has passed`() {
        logSink.storeLogs(listOf(Log()))

        moveTimeAhead(2000L)

        // Verify the logs are sent
        assertTrue(logSink.logsForNextBatch().isEmpty())
        verifyPayload(1)
    }

    @Test
    fun `logs are sent after batch time has passed`() {
        val timeStep = 500L

        repeat(9) {
            logSink.storeLogs(listOf(Log()))
            moveTimeAhead(timeStep)
        }

        // Verify no logs have been sent
        assertFalse(logSink.logsForNextBatch().isEmpty())
        verifyPayloadNotSent()

        moveTimeAhead(500)

        // Verify the logs are sent
        assertTrue(logSink.logsForNextBatch().isEmpty())
        verifyPayload(9)
    }

    @Test
    fun `flushing logs`() {
        val timeStep = 1100L

        repeat(4) {
            logSink.storeLogs(listOf(Log()))
            moveTimeAhead(timeStep)
        }

        // Verify no logs have been sent
        assertFalse(logSink.logsForNextBatch().isEmpty())
        verifyPayloadNotSent()

        // flush the logs
        logOrchestrator.flush(false)

        // Verify the logs are sent
        assertTrue(logSink.logsForNextBatch().isEmpty())
        verifyPayload(4)
    }

    @Test
    fun `flushing logs with save only enabled`() {
        val timeStep = 1100L

        repeat(4) {
            logSink.storeLogs(listOf(Log()))
            moveTimeAhead(timeStep)
        }

        // Verify no logs have been sent
        assertFalse(logSink.logsForNextBatch().isEmpty())
        verifyPayloadNotSent()

        // flush the logs
        logOrchestrator.flush(true)

        // Verify the logs are sent
        assertTrue(logSink.logsForNextBatch().isEmpty())
        assertEquals(4, store.storedLogPayloads.single().data.logs?.size)
    }

    @Test
    fun `simulate race condition`() {
        val fakeLog = Log()
        val fakeLogs = mutableListOf<Log>()
        val threads = mutableListOf<ScheduledExecutorService>()
        val latch = CountDownLatch(49)
        repeat(49) {
            fakeLogs.add(fakeLog)
            threads.add(SingleThreadTestScheduledExecutor())
        }
        logSink.storeLogs(fakeLogs)
        threads.forEach { thread ->
            thread.schedule(
                {
                    logSink.storeLogs(listOf(fakeLog))
                    latch.countDown()
                },
                10L,
                TimeUnit.MILLISECONDS,
            )
        }

        latch.await(1000L, TimeUnit.MILLISECONDS)

        assertEquals("Too many payloads sent", 1, store.storedLogPayloads.size)
        assertEquals(
            "Too many logs in payload",
            50,
            store.storedLogPayloads[0].data.logs?.size,
        )
    }

    @Test
    fun `logs with IMMEDIATE SendMode are sent immediately`() {
        logSink.storeLogs(listOf(sendImmediatelyLog))
        executorService.runCurrentlyBlocked()
        // Verify the logs are sent
        assertNull(logSink.pollUnbatchedLog())
        verifyPayload(1)
    }

    @Test
    fun `logs with DEFER SendMode are saved but not sent`() {
        logSink.storeLogs(listOf(deferredLog))
        executorService.runCurrentlyBlocked()
        // Verify the log is not in the LogSink but is saved
        assertNull(logSink.pollUnbatchedLog())

        assertEquals(1, store.storedLogPayloads.size)
    }

    @Test
    fun `batch that fails to store is tracked as an SDK error`() {
        logStoreFailures = 1
        logSink.storeLogs(listOf(Log(), Log()))
        moveTimeAhead(2000L)

        assertTrue(logSink.logsForNextBatch().isEmpty())
        verifyPayloadNotSent()
        assertEquals(SdkErrorType.PayloadStorageFail.toString(), errorHandler.sdkErrorMessages.single().msg)

        logSink.storeLogs(listOf(Log()))
        logOrchestrator.flush(false)
        verifyPayload(1)
    }

    @Test
    fun `single log that fails to store does not stop batching`() {
        logStoreFailures = 1
        logSink.storeLogs(listOf(sendImmediatelyLog))
        logSink.storeLogs(listOf(Log()))
        moveTimeAhead(2000L)

        verifyPayload(1)
        assertEquals(1, errorHandler.sdkErrorMessages.size)
    }

    private fun verifyPayload(numberOfLogs: Int) {
        assertEquals(numberOfLogs, store.storedLogPayloads.single().data.logs?.size)
    }

    private fun verifyPayloadNotSent() {
        assertTrue(store.storedLogPayloads.isEmpty())
    }

    private fun moveTimeAhead(timeStep: Long) {
        clock.tick(timeStep)
        executorService.moveForwardAndRunBlocked(timeStep)
    }
}
