package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.sdk.logs.data.LogRecordData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LogOrchestratorTest {

    companion object {
        private const val now = 123L
    }

    private lateinit var logOrchestrator: LogOrchestrator

    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var scheduledWorker: ScheduledWorker
    private val clock = FakeClock()
    private val logSink: LogSinkImpl = LogSinkImpl()
    private val deliveryService: DeliveryService = mockk(relaxed = true)

    @Before
    fun setUp() {
        executorService = BlockingScheduledExecutorService()
        scheduledWorker = ScheduledWorker(executorService)
        clock.setCurrentTime(now)
        logOrchestrator = LogOrchestrator(scheduledWorker, clock, logSink, deliveryService)
    }

    @Test
    fun `send a batch of logs when the batch max size is reached`() {
        val logs = mutableListOf<LogRecordData>()

        // Fill the sink with max batch size - 1 logs
        repeat(49) {
            logs.add(FakeLogRecordData())
        }
        logSink.storeLogs(logs.toList())

        // Verify the logs are not sent
        assertEquals(49, logSink.completedLogs().size)
        verify(exactly = 0) { deliveryService.sendLogs(any()) }

        // Add one more log to reach max batch size
        logSink.storeLogs(listOf(FakeLogRecordData()))

        // Verify the logs are sent
        assertTrue(logSink.completedLogs().isEmpty())
        verify { deliveryService.sendLogs(any()) }
    }

    @Test
    fun `logs are sent after inactivity time has passed`() {
        logSink.storeLogs(listOf(FakeLogRecordData()))

        moveTimeAhead(2500L)

        // Verify the logs are sent
        assertTrue(logSink.completedLogs().isEmpty())
        verify { deliveryService.sendLogs(any()) }
    }

    @Test
    fun `logs are sent after batch time has passed`() {
        val timeStep = 1100L

        repeat(4) {
            logSink.storeLogs(listOf(FakeLogRecordData()))
            moveTimeAhead(timeStep)
        }

        // Verify no logs have been sent
        assertFalse(logSink.completedLogs().isEmpty())
        verify(exactly = 0) { deliveryService.sendLogs(any()) }

        moveTimeAhead(timeStep)

        // Verify the logs are sent
        assertTrue(logSink.completedLogs().isEmpty())
        verify { deliveryService.sendLogs(any()) }
    }

    private fun moveTimeAhead(timeStep: Long) {
        clock.tick(timeStep)
        executorService.moveForwardAndRunBlocked(timeStep)
    }
}
