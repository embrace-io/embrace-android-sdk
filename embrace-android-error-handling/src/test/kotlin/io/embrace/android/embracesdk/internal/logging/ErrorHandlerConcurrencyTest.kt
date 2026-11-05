package io.embrace.android.embracesdk.internal.logging

import io.embrace.android.embracesdk.concurrency.runActionsConcurrently
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

internal class ErrorHandlerConcurrencyTest {

    private val limit = 10
    private val trackers = 8
    private val errorsPerTracker = 10_000
    private val maxResets = 20
    private val pool = List(40) { index ->
        IllegalStateException().apply { stackTrace = arrayOf(StackTraceElement("Foo", "bar", "Foo.kt", index)) }
    }

    @Test
    fun `tracking races resetLimit before a drain without growing the buffer past the limit`() {
        repeat(100) {
            val handler = ErrorHandlerImpl({ 0L }, { _, _, _ -> }, limit = limit)
            val finished = AtomicInteger(0)
            val trackerActions = List<() -> Unit>(trackers) { thread ->
                {
                    val errorType = if (thread == 0) SdkErrorType.ErrorSinkFail else SdkErrorType.DeliverySchedulingFail
                    repeat(errorsPerTracker / 10) {
                        handler.trackSdkError(errorType, pool[(thread * 7 + it) % pool.size])
                    }
                    finished.incrementAndGet()
                }
            }
            val resetter = {
                while (finished.get() < trackers) {
                    handler.resetLimit()
                    Thread.yield()
                }
            }
            runActionsConcurrently(trackerActions + resetter, timeoutMs = 30_000)

            val types = mutableListOf<SdkErrorType>()
            handler.drain { types += it.type }
            assertTrue("buffered ${types.size}", types.size in limit..limit + 1)
        }
    }

    @Test
    fun `tracking races drain, resetLimit and close without escaping, deadlocking or exceeding bounds`() {
        repeat(100) { runOnce() }
    }

    @Test
    fun `an error dropped because the buffer filled after its room check does not spend a limiter slot`() {
        val atRoomCheck = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val holdNext = AtomicBoolean(true)
        val handler = ErrorHandlerImpl({ 0L }, { _, _, _ -> }, limit = 1, beforeBuffering = {
            if (holdNext.getAndSet(false)) {
                atRoomCheck.countDown()
                resume.await()
            }
        })
        val racer = thread { handler.trackSdkError(SdkErrorType.DeliverySchedulingFail, pool[0]) }
        assertTrue(atRoomCheck.await(5, TimeUnit.SECONDS))
        handler.trackSdkError(SdkErrorType.DeliverySchedulingFail, pool[1])
        handler.resetLimit()
        resume.countDown()
        racer.join()

        val recorded = mutableListOf<String?>()
        handler.drain { recorded += it.top.frames.firstOrNull() }
        handler.trackSdkError(SdkErrorType.DeliverySchedulingFail, pool[0])
        assertEquals(listOf(pool[1], pool[0]).map { it.stackTrace.first().toString() }, recorded)
    }

    private fun runOnce() {
        val handler = ErrorHandlerImpl({ 0L }, { _, _, _ -> }, limit = limit, closeTimeoutMs = 10_000)
        val recorded = AtomicInteger(0)
        val recordedAfterClose = AtomicInteger(0)
        val resets = AtomicInteger(0)
        val tracked = AtomicInteger(0)
        val closeReturned = AtomicBoolean(false)
        val sink: ErrorRecorder = {
            Thread.yield()
            recorded.incrementAndGet()
            if (closeReturned.get()) {
                recordedAfterClose.incrementAndGet()
            }
        }

        val trackerActions = List(trackers) { thread ->
            {
                repeat(errorsPerTracker) {
                    handler.trackSdkError(SdkErrorType.DeliverySchedulingFail, pool[(thread * 7 + it) % pool.size])
                    tracked.incrementAndGet()
                }
            }
        }
        val drains = AtomicInteger(0)
        val drainer = {
            while (!closeReturned.get()) {
                handler.drain(sink)
                drains.incrementAndGet()
            }
        }
        val resetter = {
            while (!closeReturned.get() && resets.get() < maxResets) {
                handler.resetLimit()
                resets.incrementAndGet()
                LockSupport.parkNanos(50_000)
            }
        }
        val closer = {
            while (tracked.get() < trackers * errorsPerTracker / 2 || drains.get() == 0 || resets.get() == 0) {
                Thread.yield()
            }
            handler.close()
            closeReturned.set(true)
        }
        runActionsConcurrently(trackerActions + drainer + resetter + closer, timeoutMs = 30_000)

        assertEquals("recorded after close", 0, recordedAfterClose.get())
        assertTrue("recorded=${recorded.get()} resets=${resets.get()}", recorded.get() > 0)
        val bound = limit * (resets.get() + 1) + 1
        assertTrue("recorded ${recorded.get()} > $bound", recorded.get() <= bound)
    }
}
