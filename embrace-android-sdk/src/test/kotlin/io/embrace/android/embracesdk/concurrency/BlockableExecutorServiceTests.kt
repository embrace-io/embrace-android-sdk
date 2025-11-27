package io.embrace.android.embracesdk.concurrency

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.RejectedExecutionException

internal class BlockableExecutorServiceTests {

    private lateinit var executorService: BlockableExecutorService
    private var tasksExecuted = 0
    private val task = { tasksExecuted++ }

    @Before
    fun setup() {
        executorService = BlockableExecutorService(blockingMode = true)
    }

    @After
    fun tearDown() {
        tasksExecuted = 0
    }

    @Test
    fun `test executor executes on the current thread`() {
        var executorRunThread: Thread? = null
        executorService.submit {
            executorRunThread = Thread.currentThread()
        }
        executorService.runNext()
        assertEquals(Thread.currentThread(), executorRunThread)
    }

    @Test
    fun `test tasks blocked until told to run`() {
        repeat(3) {
            executorService.submit(task)
        }

        assertEquals(0, tasksExecuted)
        assertEquals(3, executorService.tasksBlockedCount())
        executorService.runNext()
        assertEquals(1, tasksExecuted)
        assertEquals(2, executorService.tasksBlockedCount())
        executorService.runCurrentlyBlocked()
        assertEquals(3, tasksExecuted)
        assertEquals(0, executorService.tasksBlockedCount())
    }

    @Test
    fun `test tasks queued while unblocked tasks are running will not run`() {
        repeat(3) {
            executorService.submit {
                tasksExecuted++
                executorService.submit(task)
            }
        }

        executorService.runCurrentlyBlocked()
        assertEquals(3, tasksExecuted)
        assertEquals(3, executorService.tasksBlockedCount())
        executorService.runCurrentlyBlocked()
        assertEquals(6, tasksExecuted)
        assertEquals(0, executorService.tasksBlockedCount())
    }

    @Test
    fun `tasks run immediately on the current thread in non-blocking mode`() {
        val nonBlockingModeExecutor = BlockableExecutorService(blockingMode = false)
        var executorRunThread: Thread? = null
        nonBlockingModeExecutor.submit {
            executorRunThread = Thread.currentThread()
        }
        assertEquals(Thread.currentThread(), executorRunThread)
    }

    @Test
    fun `tasks currently blocked run immediately when switching to non-blocking mode`() {
        executorService.submit(task)
        assertEquals(0, tasksExecuted)
        assertEquals(1, executorService.tasksBlockedCount())
        executorService.blockingMode = false
        assertEquals(1, tasksExecuted)
        assertEquals(0, executorService.tasksBlockedCount())
    }

    @Test
    fun `tasks blocked after enabling blocking mode`() {
        val executor = BlockableExecutorService()
        executor.submit(task)
        assertEquals(1, tasksExecuted)
        executor.blockingMode = true
        executor.submit(task)
        assertEquals(1, tasksExecuted)
        executor.runNext()
        assertEquals(2, tasksExecuted)
    }

    @Test
    fun `test shutdown`() {
        repeat(3) {
            executorService.submit {
                tasksExecuted++
                executorService.submit(task)
            }
        }

        executorService.shutdown()
        assertEquals(6, tasksExecuted)
        assertTrue(executorService.isShutdown)
        assertTrue(executorService.isTerminated)

        assertThrows(RejectedExecutionException::class.java) {
            executorService.submit {}
        }

        assertThrows(RejectedExecutionException::class.java) {
            executorService.runCurrentlyBlocked()
        }

        assertThrows(RejectedExecutionException::class.java) {
            executorService.runNext()
        }
    }

    @Test
    fun `test shutdownNow`() {
        repeat(3) {
            executorService.submit(task)
        }

        val remainingTasks = executorService.shutdownNow()
        assertEquals(3, remainingTasks.size)
        assertEquals(0, tasksExecuted)
        assertTrue(executorService.isShutdown)
        assertTrue(executorService.isTerminated)
    }
}
