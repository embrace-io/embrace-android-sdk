package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fakes.FakePayloadStore
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fixtures.deferredLogRecordData
import io.embrace.android.embracesdk.fixtures.sendImmediatelyLogRecordData
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSourceImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.opentelemetry.sdk.logs.data.LogRecordData
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
        private const val now = 123L
    }

    private lateinit var logOrchestrator: LogOrchestrator
    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var worker: BackgroundWorker
    private lateinit var logSink: LogSink
    private lateinit var store: FakePayloadStore
    private val clock = FakeClock()

    @Before
    fun setUp() {
        executorService = BlockingScheduledExecutorService()
        worker =
            BackgroundWorker(executorService)
        logSink = LogSinkImpl()
        store = FakePayloadStore()
        clock.setCurrentTime(now)
        logOrchestrator = LogOrchestratorImpl(
            worker,
            clock,
            logSink,
            store,
            FakePayloadSourceModule(
                logPayloadSource = LogPayloadSourceImpl(logSink)
            ).logEnvelopeSource
        )
        logSink.registerLogStoredCallback(logOrchestrator::onLogsAdded)
    }

    @Test
    fun `send a batch of logs when the batch max size is reached`() {
        val logs = mutableListOf<LogRecordData>()

        // Fill the sink with max batch size - 1 logs
        repeat(LogOrchestratorImpl.MAX_LOGS_PER_BATCH - 1) {
            logs.add(FakeLogRecordData())
        }
        logSink.storeLogs(logs.toList())

        // Verify the logs are not sent
        assertEquals(LogOrchestratorImpl.MAX_LOGS_PER_BATCH - 1, logSink.logsForNextBatch().size)
        verifyPayloadNotSent()

        // Add one more log to reach max batch size
        logSink.storeLogs(listOf(FakeLogRecordData()))

        // Verify the logs are sent
        assertTrue(logSink.logsForNextBatch().isEmpty())
        verifyPayload(LogOrchestratorImpl.MAX_LOGS_PER_BATCH)
    }

    @Test
    fun `logs are sent after inactivity time has passed`() {
        logSink.storeLogs(listOf(FakeLogRecordData()))

        moveTimeAhead(2000L)

        // Verify the logs are sent
        assertTrue(logSink.logsForNextBatch().isEmpty())
        verifyPayload(1)
    }

    @Test
    fun `logs are sent after batch time has passed`() {
        val timeStep = 500L

        repeat(9) {
            logSink.storeLogs(listOf(FakeLogRecordData()))
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
            logSink.storeLogs(listOf(FakeLogRecordData()))
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
            logSink.storeLogs(listOf(FakeLogRecordData()))
            moveTimeAhead(timeStep)
        }

        // Verify no logs have been sent
        assertFalse(logSink.logsForNextBatch().isEmpty())
        verifyPayloadNotSent()

        // flush the logs
        logOrchestrator.flush(true)

        // Verify the logs are sent
        assertTrue(logSink.logsForNextBatch().isEmpty())
        store.storedLogPayloads.single().let { (envelope, attemptImmediateRequest) ->
            assertEquals(4, envelope.data.logs?.size)
            assertFalse(attemptImmediateRequest)
        }
    }

    @Test
    fun `simulate race condition`() {
        val fakeLog = FakeLogRecordData()
        val fakeLogs = mutableListOf<LogRecordData>()
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
                TimeUnit.MILLISECONDS
            )
        }

        latch.await(1000L, TimeUnit.MILLISECONDS)

        assertEquals("Too many payloads sent", 1, store.storedLogPayloads.size)
        assertEquals(
            "Too many logs in payload",
            50,
            store.storedLogPayloads[0].first.data.logs?.size
        )
    }

    @Test
    fun `logs with IMMEDIATE SendMode are sent immediately`() {
        logSink.storeLogs(listOf(sendImmediatelyLogRecordData))
        executorService.runCurrentlyBlocked()
        // Verify the logs are sent
        assertNull(logSink.pollUnbatchedLog())
        verifyPayload(1)
    }

    @Test
    fun `logs with DEFER SendMode are saved but not sent`() {
        logSink.storeLogs(listOf(deferredLogRecordData))
        executorService.runCurrentlyBlocked()
        // Verify the log is not in the LogSink but is saved
        assertNull(logSink.pollUnbatchedLog())

        store.storedLogPayloads.single().let { (_, attemptImmediateRequest) ->
            assertFalse(attemptImmediateRequest)
        }
    }

    private fun verifyPayload(numberOfLogs: Int) {
        store.storedLogPayloads.single().let { (envelope, attemptImmediateRequest) ->
            assertEquals(numberOfLogs, envelope.data.logs?.size)
            assertTrue(attemptImmediateRequest)
        }
    }

    private fun verifyPayloadNotSent() {
        assertTrue(store.storedLogPayloads.isEmpty())
    }

    private fun moveTimeAhead(timeStep: Long) {
        clock.tick(timeStep)
        executorService.moveForwardAndRunBlocked(timeStep)
    }
}
