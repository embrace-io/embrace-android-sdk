package io.embrace.android.embracesdk.internal.vitals

import androidx.test.ext.junit.runners.AndroidJUnit4
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
        val scheduler = HandlerVitalsScheduler().apply { start() }
        val thread = scheduler.handler.looper.thread
        scheduler.stop()
        thread.join(TIMEOUT_MS)
        assertFalse(thread.isAlive)
    }

    @Test
    fun `work scheduled after stop is dropped`() {
        val scheduler = HandlerVitalsScheduler().apply { start() }
        scheduler.stop()
        val latch = CountDownLatch(1)

        scheduler.post { latch.countDown() }
        scheduler.scheduleSettle(0) { latch.countDown() }
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `work scheduled before stop runs`() {
        val scheduler = HandlerVitalsScheduler().apply { start() }
        val latch = CountDownLatch(1)
        scheduler.post { latch.countDown() }

        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        scheduler.stop()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
