package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeSpanService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class UninitializedSdkSpanServiceTest {

    private lateinit var service: UninitializedSdkSpanService
    private lateinit var delegate: FakeSpanService

    @Before
    fun setUp() {
        service = UninitializedSdkSpanService()
        delegate = FakeSpanService()
    }

    @Test
    fun `spans recorded before init are buffered and replayed`() {
        assertTrue(service.recordCompletedSpan("test", 0, 100))
        service.triggerBufferedSpanRecording(delegate)

        assertEquals(1, delegate.createdSpans.size)
        assertEquals("test", delegate.createdSpans.single().name)
    }

    @Test
    fun `spans recorded after init are delegated directly`() {
        service.triggerBufferedSpanRecording(delegate)

        assertTrue(service.recordCompletedSpan("after-init", 0, 100))
        assertEquals(1, delegate.createdSpans.size)
        assertEquals("after-init", delegate.createdSpans.single().name)
    }

    @Test
    fun `span recorded during triggerBufferedSpanRecording is not lost`() {
        // Buffer some spans before init
        repeat(5) { i ->
            service.recordCompletedSpan("pre-init-$i", 0, 100)
        }

        val latch = CountDownLatch(2)
        val drainStarted = CountDownLatch(1)

        val drainer = Thread {
            drainStarted.countDown()
            service.triggerBufferedSpanRecording(delegate)
            latch.countDown()
        }

        val recorder = Thread {
            drainStarted.await(1, TimeUnit.SECONDS)
            // Record a span that races with the drain
            service.recordCompletedSpan("racing-span", 0, 100)
            latch.countDown()
        }

        drainer.start()
        recorder.start()
        assertTrue(latch.await(2, TimeUnit.SECONDS))

        // All 6 spans should be recorded (5 pre-init + 1 racing)
        assertEquals(6, delegate.createdSpans.size)
        assertTrue(delegate.createdSpans.any { it.name == "racing-span" })
    }
}
