package io.embrace.android.embracesdk.concurrency

import io.embrace.android.embracesdk.fakes.FakeClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

internal class BlockingScheduledExecutorServiceTests {
    private lateinit var executorService: BlockingScheduledExecutorService
    private var tasksExecuted = 0
    private lateinit var clock: FakeClock

    @Before
    fun setup() {
        clock = FakeClock()
        executorService = BlockingScheduledExecutorService(clock)
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
        executorService.runCurrentlyBlocked()
        assertEquals(Thread.currentThread(), executorRunThread)

        executorService.schedule(
            { executorRunThread = Thread.currentThread() },
            10L,
            TimeUnit.MILLISECONDS
        )
        executorService.moveForwardAndRunBlocked(10L)

        assertEquals(Thread.currentThread(), executorRunThread)
    }

    @Test
    fun `test tasks blocked until told to run`() {
        repeat(3) {
            executorService.submit {
                tasksExecuted++
            }
        }

        assertEquals(0, tasksExecuted)
        executorService.runCurrentlyBlocked()
        assertEquals(3, tasksExecuted)
    }

    @Test
    fun `test scheduled tasks blocked until told to run`() {
        repeat(3) {
            executorService.schedule(
                { tasksExecuted++ },
                100L + it,
                TimeUnit.MILLISECONDS
            )
        }

        executorService.runCurrentlyBlocked()
        assertEquals(0, tasksExecuted)
        executorService.moveForwardAndRunBlocked(50L)
        assertEquals(0, tasksExecuted)
        executorService.moveForwardAndRunBlocked(50L)
        assertEquals(1, tasksExecuted)
        executorService.moveForwardAndRunBlocked(10L)
        assertEquals(3, tasksExecuted)
    }

    @Test
    fun `test tasks queued while unblocked tasks are running will not run`() {
        var tasksExecuted = 0
        repeat(3) {
            executorService.submit {
                tasksExecuted++
                executorService.submit {
                    tasksExecuted++
                }
            }
        }

        executorService.runCurrentlyBlocked()
        assertEquals(3, tasksExecuted)
        executorService.runCurrentlyBlocked()
        assertEquals(6, tasksExecuted)
    }

    @Test
    fun `test submit`() {
        val taskStatus = executorService.submit {
            tasksExecuted++
        }
        assertFalse(taskStatus.isDone)
        executorService.runCurrentlyBlocked()
        assertEquals(1, tasksExecuted)
        assertTrue(taskStatus.isDone)
    }

    @Test
    fun `test cancellation via future from submit`() {
        val taskStatus = executorService.submit {
            tasksExecuted++
        }
        assertFalse(taskStatus.isDone)
        assertFalse(taskStatus.isCancelled)
        taskStatus.cancel(true)
        executorService.runCurrentlyBlocked()
        assertEquals(0, tasksExecuted)
        assertTrue(taskStatus.isDone)
        assertTrue(taskStatus.isCancelled)
    }

    @Test
    fun `test schedule runnable`() {
        val taskStatus = executorService.schedule(
            command = { tasksExecuted++ },
            delay = 10L,
            unit = TimeUnit.SECONDS
        )
        assertFalse(taskStatus.isDone)
        executorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(10L))
        assertEquals(1, tasksExecuted)
        assertTrue(taskStatus.isDone)
    }

    @Test
    fun `test schedule callable`() {
        val taskStatus = executorService.schedule(
            callable = { tasksExecuted++ },
            delay = 10L,
            unit = TimeUnit.SECONDS
        )
        assertFalse(taskStatus.isDone)
        executorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(10L))
        assertEquals(1, tasksExecuted)
        assertTrue(taskStatus.isDone)
        // welp, callable result not returned
        assertEquals(0, taskStatus.get())
    }

    @Test
    fun `test cancellation via future from schedule`() {
        val taskStatus = executorService.schedule(
            command = { tasksExecuted++ },
            delay = 10L,
            unit = TimeUnit.SECONDS
        )
        assertFalse(taskStatus.isDone)
        assertFalse(taskStatus.isCancelled)
        taskStatus.cancel(true)
        executorService.moveForwardAndRunBlocked(TimeUnit.SECONDS.toMillis(10L))
        assertEquals(0, tasksExecuted)
        assertTrue(taskStatus.isDone)
        assertTrue(taskStatus.isCancelled)
    }

    @Test
    fun `test scheduleAtFixedRate`() {
        val taskStatus = executorService.scheduleAtFixedRate(
            command = { tasksExecuted++ },
            initialDelay = 500L,
            period = 10L,
            unit = TimeUnit.MILLISECONDS
        )
        assertFalse(taskStatus.isDone)
        executorService.runCurrentlyBlocked()
        assertEquals(0, tasksExecuted)
        executorService.moveForwardAndRunBlocked(500L)
        executorService.runCurrentlyBlocked()
        assertEquals(1, tasksExecuted)
        executorService.moveForwardAndRunBlocked(10L)
        assertEquals(2, tasksExecuted)
        executorService.moveForwardAndRunBlocked(9L)
        assertEquals(2, tasksExecuted)
        executorService.moveForwardAndRunBlocked(1L)
        assertEquals(3, tasksExecuted)
        assertFalse(taskStatus.isDone)
        taskStatus.cancel(true)
        assertTrue(taskStatus.isDone)
        assertTrue(taskStatus.isCancelled)
        executorService.moveForwardAndRunBlocked(10L)
        assertEquals(3, tasksExecuted)
    }

    @Test
    fun `test delayed runs of scheduleAtFixedRate`() {
        executorService.scheduleAtFixedRate(
            command = { tasksExecuted++ },
            initialDelay = 0L,
            period = 10L,
            unit = TimeUnit.MILLISECONDS
        )
        executorService.runCurrentlyBlocked()
        assertEquals(1, tasksExecuted)
        executorService.moveForwardAndRunBlocked(40L)
        assertEquals(5, tasksExecuted)
    }

    @Test
    fun `test scheduleWithFixedDelay`() {
        val taskStatus = executorService.scheduleWithFixedDelay(
            command = {
                clock.tick(100L)
                tasksExecuted++
            },
            initialDelay = 500L,
            delay = 10L,
            unit = TimeUnit.MILLISECONDS
        )
        assertFalse(taskStatus.isDone)
        executorService.runCurrentlyBlocked()
        assertEquals(0, tasksExecuted)
        executorService.moveForwardAndRunBlocked(500L)
        executorService.runCurrentlyBlocked()
        assertEquals(1, tasksExecuted)
        executorService.moveForwardAndRunBlocked(10L)
        assertEquals(2, tasksExecuted)
        executorService.moveForwardAndRunBlocked(9L)
        assertEquals(2, tasksExecuted)
        executorService.moveForwardAndRunBlocked(1L)
        assertEquals(3, tasksExecuted)
        assertFalse(taskStatus.isDone)
        taskStatus.cancel(true)
        assertTrue(taskStatus.isDone)
        assertTrue(taskStatus.isCancelled)
        executorService.moveForwardAndRunBlocked(10L)
        assertEquals(3, tasksExecuted)
    }

    @Test
    fun `test delayed runs of scheduleWithFixedDelay`() {
        executorService.scheduleWithFixedDelay(
            command = {
                clock.tick(100L)
                tasksExecuted++
            },
            initialDelay = 0L,
            delay = 10L,
            unit = TimeUnit.MILLISECONDS
        )
        executorService.runCurrentlyBlocked()
        assertEquals(1, tasksExecuted)
        executorService.moveForwardAndRunBlocked(99L)
        assertEquals(2, tasksExecuted)
    }

    @Test
    fun `test invalid parameters for scheduling Runnable`() {
        val nullRunnable: Runnable? = null
        assertThrows(IllegalArgumentException::class.java) {
            executorService.schedule(nullRunnable, 0L, TimeUnit.MILLISECONDS)
        }

        assertThrows(IllegalArgumentException::class.java) {
            executorService.schedule(Runnable {}, -1L, TimeUnit.MILLISECONDS)
        }

        assertThrows(IllegalArgumentException::class.java) {
            executorService.schedule(Runnable {}, 0L, null)
        }
    }

    @Test
    fun `test invalid parameters for scheduling Callable`() {
        val nullCallable: Callable<Unit>? = null
        assertThrows(IllegalArgumentException::class.java) {
            executorService.schedule(nullCallable, 0L, TimeUnit.MILLISECONDS)
        }

        assertThrows(IllegalArgumentException::class.java) {
            executorService.schedule(Callable {}, -1L, TimeUnit.MILLISECONDS)
        }

        assertThrows(IllegalArgumentException::class.java) {
            executorService.schedule(Callable {}, 0L, null)
        }
    }

    @Test
    fun `test invalid parameters for scheduleAtFixedRate`() {
        assertThrows(IllegalArgumentException::class.java) {
            executorService.scheduleAtFixedRate(null, 0L, 100L, TimeUnit.MILLISECONDS)
        }

        assertThrows(IllegalArgumentException::class.java) {
            executorService.scheduleAtFixedRate({}, -1L, 100L, TimeUnit.MILLISECONDS)
        }

        assertThrows(IllegalArgumentException::class.java) {
            executorService.scheduleAtFixedRate({}, 0L, 0L, TimeUnit.MILLISECONDS)
        }

        assertThrows(IllegalArgumentException::class.java) {
            executorService.scheduleAtFixedRate({}, 0L, 100L, null)
        }
    }

    @Test
    fun `test invalid parameters for scheduleWithFixedDelay`() {
        assertThrows(IllegalArgumentException::class.java) {
            executorService.scheduleWithFixedDelay(null, 0L, 100L, TimeUnit.MILLISECONDS)
        }

        assertThrows(IllegalArgumentException::class.java) {
            executorService.scheduleWithFixedDelay({}, -1L, 100L, TimeUnit.MILLISECONDS)
        }

        assertThrows(IllegalArgumentException::class.java) {
            executorService.scheduleWithFixedDelay({}, 0L, 0L, TimeUnit.MILLISECONDS)
        }

        assertThrows(IllegalArgumentException::class.java) {
            executorService.scheduleWithFixedDelay({}, 0L, 100L, null)
        }
    }

    @Test
    fun `test shutdown`() {
        repeat(3) {
            executorService.submit {
                tasksExecuted++
                executorService.submit { tasksExecuted++ }
            }
        }

        repeat(2) {
            executorService.schedule(
                { tasksExecuted++ },
                100L + it,
                TimeUnit.MILLISECONDS
            )
        }

        executorService.shutdown()
        assertEquals(8, tasksExecuted)
        assertTrue(executorService.isShutdown)
        assertTrue(executorService.isTerminated)

        assertThrows(RejectedExecutionException::class.java) {
            executorService.submit {}
        }

        assertThrows(RejectedExecutionException::class.java) {
            executorService.runCurrentlyBlocked()
        }

        assertThrows(RejectedExecutionException::class.java) {
            executorService.moveForwardAndRunBlocked(10L)
        }
    }

    @Test
    fun `test shutdownNow`() {
        repeat(3) {
            executorService.submit { tasksExecuted++ }
        }

        repeat(2) {
            executorService.schedule(
                { tasksExecuted++ },
                100L + it,
                TimeUnit.MILLISECONDS
            )
        }

        val remainingTasks = executorService.shutdownNow()
        assertEquals(5, remainingTasks.size)
        assertEquals(0, tasksExecuted)
        assertTrue(executorService.isShutdown)
        assertTrue(executorService.isTerminated)
    }
}
