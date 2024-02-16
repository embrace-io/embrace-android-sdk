package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class LogOrchestratorTest {

    companion object {
        private const val now = 123L
    }

    private lateinit var logOrchestrator: LogOrchestrator
    private lateinit var onLogsStored: () -> Unit

    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var scheduledWorker: ScheduledWorker
    private val clock = FakeClock()
    private val logSink: LogSinkImpl = mockk(relaxed = true)

    @Before
    fun setUp() {
        executorService = BlockingScheduledExecutorService()
        scheduledWorker = ScheduledWorker(executorService)
        clock.setCurrentTime(now)
        val onLogsStoredSlot = slot<() -> Unit>()
        every {
            logSink.callOnLogsStored(capture(onLogsStoredSlot))
        } returns Unit
        logOrchestrator = LogOrchestrator(scheduledWorker, clock, logSink)
        onLogsStored = onLogsStoredSlot.captured
    }

    @Test
    fun `a listener is set on initialization`() {
        assertNotNull(onLogsStored)
    }

    @Test
    fun `send a batch of logs when the batch max size is reached`() {
        val logs = mutableListOf<EmbraceLogRecordData>()
        every { logSink.completedLogs() } returns logs

        // Fill the sink with max batch size - 1 logs
        repeat(49) {
            logs.add(mockk())
        }
        onLogsStored()

        // Verify the logs are not sent
        verify(exactly = 0) { logSink.flushLogs() }
        // TODO Verify on a mocked DeliveryService when available

        // Add one more log to reach max batch size
        logs.add(mockk())
        onLogsStored()

        // Verify the logs are sent
        verify { logSink.flushLogs() }
        // TODO Verify on a mocked DeliveryService when available
    }

    @Test
    fun `logs are sent after inactivity time has passed`() {
        every { logSink.completedLogs() } returns listOf(mockk())

        onLogsStored()
        moveTimeAhead(2500L)

        // Verify the logs are sent
        verify { logSink.flushLogs() }
        // TODO Verify on a mocked DeliveryService when availables
    }

    @Test
    fun `logs are sent after batch time has passed`() {
        val logs = mutableListOf<EmbraceLogRecordData>()
        every { logSink.completedLogs() } returns logs

        val timeStep = 1100L

        repeat(4) {
            logs.add(mockk())
            onLogsStored()
            moveTimeAhead(timeStep)
        }

        // Verify no logs have been sent
        verify(exactly = 0) { logSink.flushLogs() }
        // TODO Verify on a mocked DeliveryService when available

        moveTimeAhead(timeStep)

        // Verify the logs are sent
        verify { logSink.flushLogs() }
        // TODO Verify on a mocked DeliveryService when available
    }

    private fun moveTimeAhead(timeStep: Long) {
        clock.tick(timeStep)
        executorService.moveForwardAndRunBlocked(timeStep)
    }
}
