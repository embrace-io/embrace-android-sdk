package io.embrace.android.embracesdk.internal.vitals

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class HandlerVitalsSchedulerTest {

    @Test
    fun `stop ends the backing thread`() {
        val scheduler = HandlerVitalsScheduler(FakeErrorHandler()).apply { start() }
        val thread = scheduler.handler.looper.thread
        scheduler.stop()
        thread.join(TIMEOUT_MS)
        assertFalse(thread.isAlive)
    }

    @Test
    fun `work scheduled after stop is dropped`() {
        val scheduler = HandlerVitalsScheduler(FakeErrorHandler()).apply { start() }
        val thread = scheduler.handler.looper.thread
        scheduler.stop()
        val latch = CountDownLatch(1)

        scheduler.post { latch.countDown() }
        scheduler.scheduleSettle(0) { latch.countDown() }
        thread.join(TIMEOUT_MS)
        assertEquals(1, latch.count)
    }

    @Test
    fun `work scheduled before stop runs`() {
        val scheduler = HandlerVitalsScheduler(FakeErrorHandler()).apply { start() }
        val latch = CountDownLatch(1)
        scheduler.post { latch.countDown() }

        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        scheduler.stop()
    }

    @Test
    fun `work that throws is tracked and the thread keeps running`() {
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val scheduler = HandlerVitalsScheduler(errorHandler).apply { start() }
        val latch = CountDownLatch(1)
        scheduler.post { error("boom") }
        scheduler.post { latch.countDown() }

        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        assertEquals(1, errorHandler.sdkErrorMessages.size)
        scheduler.stop()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
