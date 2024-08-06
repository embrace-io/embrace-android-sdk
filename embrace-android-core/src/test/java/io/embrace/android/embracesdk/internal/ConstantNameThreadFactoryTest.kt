package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class ConstantNameThreadFactoryTest {
    private lateinit var executor: SingleThreadTestScheduledExecutor

    @Test
    fun `verify constant default thread name`() {
        executor = SingleThreadTestScheduledExecutor(ConstantNameThreadFactory())
        verifyThreadNames("emb-thread")
    }

    @Test
    fun `verify constant thread name with custom token`() {
        executor = SingleThreadTestScheduledExecutor(ConstantNameThreadFactory(namePrefix = "blob"))
        verifyThreadNames("emb-blob")
    }

    @Test
    fun `verify thread name with unique instance token only`() {
        val threadFactory = ConstantNameThreadFactory(uniquePerInstance = true)
        val token = threadFactory.hashCode()
        executor = SingleThreadTestScheduledExecutor(threadFactory)
        verifyThreadNames("emb-thread-$token")
    }

    @Test
    fun `verify constant thread name with unique instance token`() {
        val threadFactory = ConstantNameThreadFactory(namePrefix = "plop", uniquePerInstance = true)
        val token = threadFactory.hashCode()
        executor = SingleThreadTestScheduledExecutor(threadFactory)
        verifyThreadNames("emb-plop-$token")
    }

    private fun verifyThreadNames(expectedName: String) {
        val countDownLatch = CountDownLatch(1)
        executor.setKeepAliveTime(1L, TimeUnit.MILLISECONDS)
        executor.allowCoreThreadTimeOut(true)
        val firstThread = AtomicReference<Thread>()
        val secondThread = AtomicReference<Thread>()
        executor.submit { firstThread.set(Thread.currentThread()) }.get(1L, TimeUnit.SECONDS)
        assertNotNull(firstThread.get())
        assertEquals(expectedName, firstThread.get().name)

        // Wait long enough for the existing thread in the executor to time out. A better way to do this would be nice...
        countDownLatch.await(100L, TimeUnit.MILLISECONDS)
        executor.submit { secondThread.set(Thread.currentThread()) }.get(1L, TimeUnit.SECONDS)
        assertNotNull(secondThread.get())
        assertEquals(firstThread.get().name, secondThread.get().name)
        assertNotEquals(firstThread.get().id, secondThread.get().id)
        assertNotEquals(firstThread.get(), secondThread.get())
    }
}
