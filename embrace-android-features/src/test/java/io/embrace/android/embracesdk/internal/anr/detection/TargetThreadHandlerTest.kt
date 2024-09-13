package io.embrace.android.embracesdk.internal.anr.detection

import android.os.Message
import android.os.MessageQueue
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.behavior.FakeAnrBehavior
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
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
        executorService = BlockingScheduledExecutorService(blockingMode = true)
        executorService.submit { anrMonitorThread.set(Thread.currentThread()) }
        executorService.runCurrentlyBlocked()
        handler = createHandler(null)
        handler.action = mockk()
    }

    private fun createHandler(messageQueue: MessageQueue?): TargetThreadHandler {
        return TargetThreadHandler(
            mockk(relaxed = true),
            ScheduledWorker(executorService),
            configService,
            messageQueue,
            logger = EmbLoggerImpl()
        ) { FAKE_TIME_MS }.apply {
            action = {}
        }
    }

    @Test
    fun testTargetThreadHandlerWrongMsg() {
        Assert.assertNotNull(handler)
        state.lastTargetThreadResponseMs = 0L

        // process a message
        handler.handleMessage(mockk(relaxed = true))
        verify(exactly = 0) { handler.action.invoke(any()) }
    }

    @Test
    fun testNoAnrInProgress() {
        Assert.assertNotNull(handler)
        state.lastTargetThreadResponseMs = 0L

        // process a message
        val msg = mockk<Message>()
        msg.what = TargetThreadHandler.HEARTBEAT_REQUEST
        handler.handleMessage(msg)
        Assert.assertEquals(2, executorService.submitCount)
    }

    @Test
    fun testTargetThreadHandlerCorrectMsg() {
        Assert.assertNotNull(handler)
        state.lastTargetThreadResponseMs = 0L
        state.anrInProgress = true

        // process a message
        val msg = mockk<Message>()
        msg.what = TargetThreadHandler.HEARTBEAT_REQUEST
        handler.handleMessage(msg)
        Assert.assertEquals(2, executorService.submitCount)
        executorService.runCurrentlyBlocked()
        verify { handler.action.invoke(FAKE_TIME_MS) }
    }

    @Test
    fun testCorrectMsgNonNullQueue() {
        handler = createHandler(mockk(relaxed = true))
        handler.installed = true
        Assert.assertNotNull(handler)
        state.lastTargetThreadResponseMs = 0L
        state.anrInProgress = true

        // process a message
        val msg = mockk<Message>()
        msg.what = TargetThreadHandler.HEARTBEAT_REQUEST
        handler.handleMessage(msg)
    }

    @Test
    fun testRejectedExecution() {
        executorService.shutdownNow()
        Assert.assertNotNull(handler)
        state.lastTargetThreadResponseMs = 0L

        // RejectedExecutionException ignored as ScheduledExecutorService will be shutting down.
        handler.handleMessage(mockk(relaxed = true))
        Assert.assertEquals(0L, state.lastTargetThreadResponseMs)
    }

    @Test
    fun testStartIdleHandlerEnabled() {
        val messageQueue = mockk<MessageQueue>(relaxed = true)
        configService = FakeConfigService(
            anrBehavior = FakeAnrBehavior(idleHandlerEnabled = true)
        )
        handler = createHandler(messageQueue)
        handler.start()
        verify(exactly = 1) { messageQueue.addIdleHandler(any()) }
    }

    @Test
    fun testStartIdleHandlerDisabled() {
        val messageQueue = mockk<MessageQueue>(relaxed = true)
        configService = FakeConfigService(
            anrBehavior = FakeAnrBehavior(idleHandlerEnabled = false)
        )
        handler = createHandler(messageQueue)
        handler.start()
        verify(exactly = 0) { messageQueue.addIdleHandler(any()) }
    }
}
