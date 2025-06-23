package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class ThreadLocalExtensionsTest {

    private val id: Long by threadLocal {
        Thread.currentThread().compatThreadId()
    }

    @Test
    fun testThreadLocalProperties() {
        val testThreadId = Thread.currentThread().compatThreadId()
        assertEquals(id, testThreadId)

        val latch = CountDownLatch(1)
        Executors.newSingleThreadExecutor().submit {
            assertEquals(id, Thread.currentThread().compatThreadId())
            assertNotEquals(id, testThreadId)
            latch.countDown()
        }
        latch.await(1, TimeUnit.SECONDS)
    }
}
