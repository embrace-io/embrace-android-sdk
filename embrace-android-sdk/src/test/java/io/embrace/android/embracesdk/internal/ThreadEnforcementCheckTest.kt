package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.Thread.currentThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class ThreadEnforcementCheckTest {

    private lateinit var executor: SingleThreadTestScheduledExecutor

    @Before
    fun setup() {
        executor = SingleThreadTestScheduledExecutor()
    }

    @Test
    fun testCorrectThread() {
        enforceThread(AtomicReference(currentThread())) // no exception thrown
    }

    @Test
    fun `wrong thread throws exception in debug only`() {
        if (BuildConfig.DEBUG) {
            assertThrows(WrongThreadException::class.java) {
                enforceThread(nonExecutingThread)
            }
        } else {
            enforceThread(nonExecutingThread)
        }
    }

    @Test
    fun `wrong thread in executor task will throw swallowed exception in debug`() {
        val latch = CountDownLatch(1)
        val future = executor.submit {
            enforceThread(nonExecutingThread)
            latch.countDown()
        }

        var executionExceptionThrown = false
        try {
            future.get(1L, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            assertTrue(e.cause is WrongThreadException)
            executionExceptionThrown = true
        }

        if (BuildConfig.DEBUG) {
            assertTrue(executionExceptionThrown)
            assertEquals(1, latch.count)
        } else {
            assertFalse(executionExceptionThrown)
            assertEquals(0, latch.count)
        }
    }

    @Test
    fun `different threads with the same name is not considered wrong`() {
        executor.allowCoreThreadTimeOut(true)
        executor.setKeepAliveTime(1L, TimeUnit.MILLISECONDS)

        val firstThread = AtomicReference<Thread>()
        val secondThread = AtomicReference<Thread>()
        executor.submit { firstThread.set(currentThread()) }.get(1L, TimeUnit.SECONDS)
        assertNotNull(firstThread.get())

        // Wait long enough for the existing thread in the executor to time out. A better way to do this would be nice...
        Thread.sleep(100L)
        executor.submit {
            secondThread.set(currentThread())
            enforceThread(firstThread)
        }.get(1L, TimeUnit.SECONDS)
        assertNotEquals(firstThread.get(), secondThread.get())
    }

    companion object {
        private val nonExecutingThread = AtomicReference(Thread())
    }
}
