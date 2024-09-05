package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fixtures.deferredLogRecordData
import io.embrace.android.embracesdk.fixtures.unbatchableLogRecordData
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSourceImpl
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import io.opentelemetry.sdk.logs.data.LogRecordData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private lateinit var scheduledWorker: ScheduledWorker
    private lateinit var logSink: LogSink
    private lateinit var deliveryService: FakeDeliveryService
    private val clock = FakeClock()

    @Before
    fun setUp() {
        executorService = BlockingScheduledExecutorService()
        scheduledWorker = ScheduledWorker(executorService)
        logSink = LogSinkImpl()
        deliveryService = FakeDeliveryService()
        clock.setCurrentTime(now)
        logOrchestrator = LogOrchestratorImpl(
            scheduledWorker,
            clock,
            logSink,
            deliveryService,
            FakePayloadSourceModule(
                logPayloadSource = LogPayloadSourceImpl(logSink)
            ).logEnvelopeSource
        )
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
        assertEquals(LogOrchestratorImpl.MAX_LOGS_PER_BATCH - 1, logSink.completedLogs().size)
        verifyPayloadNotSent()

        // Add one more log to reach max batch size
        logSink.storeLogs(listOf(FakeLogRecordData()))

        // Verify the logs are sent
        assertTrue(logSink.completedLogs().isEmpty())
        verifyPayload(LogOrchestratorImpl.MAX_LOGS_PER_BATCH)
    }

    @Test
    fun `logs are sent after inactivity time has passed`() {
        logSink.storeLogs(listOf(FakeLogRecordData()))

        moveTimeAhead(2000L)

        // Verify the logs are sent
        assertTrue(logSink.completedLogs().isEmpty())
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
        assertFalse(logSink.completedLogs().isEmpty())
        verifyPayloadNotSent()

        moveTimeAhead(500)

        // Verify the logs are sent
        assertTrue(logSink.completedLogs().isEmpty())
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
        assertFalse(logSink.completedLogs().isEmpty())
        verifyPayloadNotSent()

        // flush the logs
        logOrchestrator.flush(false)

        // Verify the logs are sent
        assertTrue(logSink.completedLogs().isEmpty())
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
        assertFalse(logSink.completedLogs().isEmpty())
        verifyPayloadNotSent()

        // flush the logs
        logOrchestrator.flush(true)

        // Verify the logs are sent
        assertTrue(logSink.completedLogs().isEmpty())
        assertEquals(0, deliveryService.lastSentLogPayloads.size)
        assertEquals(1, deliveryService.lastSavedLogPayloads.size)
        assertEquals(4, deliveryService.lastSavedLogPayloads[0].data.logs?.size)
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

        assertEquals("Too many payloads sent", 1, deliveryService.lastSentLogPayloads.size)
        assertEquals("Too many logs in payload", 50, deliveryService.lastSentLogPayloads[0].data.logs?.size)
    }

    @Test
    fun `logs with IMMEDIATE SendMode are sent immediately`() {
        logSink.storeLogs(listOf(unbatchableLogRecordData))
        executorService.runCurrentlyBlocked()
        // Verify the logs are sent
        assertNull(logSink.pollNonbatchedLog())
        verifyPayload(1)
    }

    @Test
    fun `logs with DEFER SendMode are saved by not sent`() {
        logSink.storeLogs(listOf(deferredLogRecordData))
        executorService.runCurrentlyBlocked()
        // Verify the log is not in the LogSink but is saved
        assertNull(logSink.pollNonbatchedLog())
        assertNotNull(deliveryService.lastSavedLogPayloads.single())
        assertEquals(0, deliveryService.lastSentLogPayloads.size)
    }

    private fun verifyPayload(numberOfLogs: Int) {
        assertNotNull(deliveryService.lastSentLogPayloads)
        assertEquals(1, deliveryService.lastSentLogPayloads.size)
        assertEquals(numberOfLogs, deliveryService.lastSentLogPayloads[0].data.logs?.size)
    }

    private fun verifyPayloadNotSent() {
        assertEquals(0, deliveryService.lastSentLogPayloads.size)
    }

    private fun moveTimeAhead(timeStep: Long) {
        clock.tick(timeStep)
        executorService.moveForwardAndRunBlocked(timeStep)
    }
}
