package io.embrace.android.embracesdk.internal.vitals

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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

    @Test
    fun `main-thread work runs on the main looper`() {
        val scheduler = HandlerVitalsScheduler(FakeErrorHandler()).apply { start() }
        var ranOn: Thread? = null
        scheduler.postToMain { ranOn = Thread.currentThread() }

        shadowOf(Looper.getMainLooper()).idle()
        assertSame(Looper.getMainLooper().thread, ranOn)
        scheduler.stop()
    }

    @Test
    fun `main-thread work scheduled after stop is dropped`() {
        val scheduler = HandlerVitalsScheduler(FakeErrorHandler()).apply { start() }
        scheduler.stop()
        var ran = false
        scheduler.postToMain { ran = true }

        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(ran)
    }

    @Test
    fun `main-thread work that throws is tracked`() {
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val scheduler = HandlerVitalsScheduler(errorHandler).apply { start() }
        scheduler.postToMain { error("boom") }

        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, errorHandler.sdkErrorMessages.size)
        scheduler.stop()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
