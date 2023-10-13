package io.embrace.android.embracesdk.concurrency

import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

internal class SingleThreadTestScheduledExecutorTest {
    private lateinit var executorService: SingleThreadTestScheduledExecutor

    @Before
    fun setup() {
        executorService = SingleThreadTestScheduledExecutor()
    }

    @Test
    fun `last throwable from execution captured and executor can be reset`() {
        val f1 = executorService.submit { throw RuntimeException() }
        Thread.sleep(50L)
        assertThrows(ExecutionException::class.java) {
            f1.get(1L, TimeUnit.SECONDS)
        }

        val t1 = executorService.lastThrowable()
        checkNotNull(t1)
        assertTrue("Last throwable is ${t1::class}, not RuntimeException", t1 is RuntimeException)

        executorService.reset()
        assertNull(executorService.lastThrowable())

        val f2 = executorService.submit { throw NotImplementedError() }
        Thread.sleep(50L)
        assertThrows(ExecutionException::class.java) {
            f2.get(1L, TimeUnit.SECONDS)
        }

        val t2 = executorService.lastThrowable()
        checkNotNull(t2)
        assertTrue("Last throwable is ${t2::class}, not NotImplementedError", t2 is NotImplementedError)
    }
}
