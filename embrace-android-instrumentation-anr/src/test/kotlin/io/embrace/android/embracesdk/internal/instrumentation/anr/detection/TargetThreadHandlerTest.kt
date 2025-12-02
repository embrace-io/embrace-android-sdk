package io.embrace.android.embracesdk.internal.instrumentation.anr.detection

import android.os.Message
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

private const val FAKE_TIME_MS = 16098230498234

internal class TargetThreadHandlerTest {

    private val clock = { FAKE_TIME_MS }
    private val state = ThreadMonitoringState(clock)
    private lateinit var runnable: Runnable
    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var anrMonitorThread: AtomicReference<Thread>
    private lateinit var handler: TargetThreadHandler
    private lateinit var configService: ConfigService

    @Before
    fun setUp() {
        runnable = Runnable {}
        configService = FakeConfigService()
        anrMonitorThread = AtomicReference()
        executorService = BlockingScheduledExecutorService()
        executorService.submit { anrMonitorThread.set(Thread.currentThread()) }
        executorService.runCurrentlyBlocked()
        handler = createHandler()
    }

    private fun createHandler(): TargetThreadHandler {
        return TargetThreadHandler(
            mockk(relaxed = true),
            BackgroundWorker(executorService),
            { FAKE_TIME_MS },
            action = {}
        )
    }

    @Test
    fun testTargetThreadHandlerWrongMsg() {
        assertNotNull(handler)
        state.lastTargetThreadResponseMs = 0L

        // process a message
        handler.handleMessage(mockk(relaxed = true))
        assertEquals(1, executorService.submitCount)
    }

    @Test
    fun testNoAnrInProgress() {
        assertNotNull(handler)
        state.lastTargetThreadResponseMs = 0L

        // process a message
        val msg = mockk<Message>()
        msg.what = HEARTBEAT_REQUEST
        handler.handleMessage(msg)
        assertEquals(2, executorService.submitCount)
    }

    @Test
    fun testTargetThreadHandlerCorrectMsg() {
        assertNotNull(handler)
        state.lastTargetThreadResponseMs = 0L
        state.anrInProgress = true

        // process a message
        val msg = mockk<Message>()
        msg.what = HEARTBEAT_REQUEST
        handler.handleMessage(msg)
        assertEquals(2, executorService.submitCount)
        executorService.runCurrentlyBlocked()
    }

    @Test
    fun testCorrectMsgNonNullQueue() {
        handler = createHandler()
        assertNotNull(handler)
        state.lastTargetThreadResponseMs = 0L
        state.anrInProgress = true

        // process a message
        val msg = mockk<Message>()
        msg.what = HEARTBEAT_REQUEST
        handler.handleMessage(msg)
    }

    @Test
    fun testRejectedExecution() {
        executorService.shutdownNow()
        assertNotNull(handler)
        state.lastTargetThreadResponseMs = 0L

        // RejectedExecutionException ignored as ScheduledExecutorService will be shutting down.
        handler.handleMessage(mockk(relaxed = true))
        assertEquals(0L, state.lastTargetThreadResponseMs)
    }
}
