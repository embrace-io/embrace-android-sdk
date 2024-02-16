package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

internal class LogOrchestratorTest {

    companion object {
        private const val now = 123L
    }

    @SpyK
    private lateinit var logOrchestrator: LogOrchestrator

    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var scheduledWorker: ScheduledWorker
    private val clock = FakeClock()
    private val logSink: LogSink = mockk(relaxed = true)

    @Before
    fun setUp() {
        executorService = BlockingScheduledExecutorService()
        scheduledWorker = ScheduledWorker(executorService)
        clock.setCurrentTime(now)
        logOrchestrator = LogOrchestrator(scheduledWorker, clock, logSink)
    }

    @Test
    fun `send a batch of logs when the batch max size is reached`() {
        val logs = mutableListOf<EmbraceLogRecordData>()
        every { logSink.completedLogs() } returns logs

        // Fill the sink with max batch size - 1 logs
        for (i in 1..49) {
            logs.add(mockk())
        }
        logOrchestrator.onLogsAdded()

        // Verify the logs are not sent
        verify(exactly = 0) { logSink.flushLogs() }
        verify(exactly = 0) { logOrchestrator.sendLogs() }

        // Add one more log to reach max batch size
        logs.add(mockk())
        logOrchestrator.onLogsAdded()

        // Verify the logs are sent
        verify { logSink.flushLogs() }
        verify { logOrchestrator.sendLogs() }
    }

    @Test
    fun `logs are sent after inactivity time has passed`() {
        every { logSink.completedLogs() } returns listOf(mockk())

        logOrchestrator.onLogsAdded()
        val timeAhead = 2500L
        clock.tick(timeAhead)
        executorService.moveForwardAndRunBlocked(timeAhead)

        // Verify the logs are sent
        verify { logSink.flushLogs() }
        verify { logOrchestrator.sendLogs() }
    }

    @Test
    fun `logs are sent after batch time has passed`() {
        val logs = mutableListOf<EmbraceLogRecordData>()
        every { logSink.completedLogs() } returns logs

        val timeStep = 1100L

        for (i in 1..4) {
            logs.add(mockk())
            logOrchestrator.onLogsAdded()
            clock.tick(timeStep)
            executorService.moveForwardAndRunBlocked(timeStep)
        }

        // Verify no logs have been sent
        verify(exactly = 0) { logSink.flushLogs() }
        verify(exactly = 0) { logOrchestrator.sendLogs() }

        logOrchestrator.onLogsAdded()
        clock.tick(timeStep)
        executorService.moveForwardAndRunBlocked(timeStep)

        // Verify the logs are sent
        print("Verifying...")
        verify { logSink.flushLogs() }
        verify { logOrchestrator.sendLogs() }
    }
}