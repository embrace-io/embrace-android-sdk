package io.embrace.android.embracesdk.internal.vitals

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
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
        val scheduler = HandlerVitalsScheduler(FakeInternalLogger()).apply { start() }
        val thread = scheduler.handler.looper.thread
        scheduler.stop()
        thread.join(TIMEOUT_MS)
        assertFalse(thread.isAlive)
    }

    @Test
    fun `work scheduled after stop is dropped`() {
        val scheduler = HandlerVitalsScheduler(FakeInternalLogger()).apply { start() }
        scheduler.stop()
        val latch = CountDownLatch(1)

        scheduler.post { latch.countDown() }
        scheduler.scheduleSettle(0) { latch.countDown() }
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `work scheduled before stop runs`() {
        val scheduler = HandlerVitalsScheduler(FakeInternalLogger()).apply { start() }
        val latch = CountDownLatch(1)
        scheduler.post { latch.countDown() }

        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        scheduler.stop()
    }

    @Test
    fun `work that throws is tracked and the thread keeps running`() {
        val logger = FakeInternalLogger(throwOnInternalError = false)
        val scheduler = HandlerVitalsScheduler(logger).apply { start() }
        val latch = CountDownLatch(1)
        scheduler.post { error("boom") }
        scheduler.post { latch.countDown() }

        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        assertEquals(1, logger.internalErrorMessages.size)
        scheduler.stop()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
