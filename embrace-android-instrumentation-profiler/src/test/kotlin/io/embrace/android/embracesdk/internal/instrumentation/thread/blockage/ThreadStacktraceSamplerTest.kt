package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.concurrency.runActionsConcurrently
import io.embrace.android.embracesdk.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class ThreadStacktraceSamplerTest {

    private val sampleLimit = 100
    private lateinit var clock: FakeClock
    private lateinit var sampler: ThreadStacktraceSampler

    @Before
    fun setUp() {
        clock = FakeClock()
        sampler = ThreadStacktraceSampler(
            clock,
            Thread.currentThread(),
            sampleLimit,
            200,
        )
    }

    @Test
    fun `test empty`() {
        assertTrue(sampler.retrieveSampleMetadata().isEmpty())
        assertFalse(sampler.retrieveSampleMetadata() is MutableList)
    }

    @Test
    fun `test sample max limit`() {
        repeat(1050) {
            sampler.captureSample()
        }
        val metadata = sampler.retrieveSampleMetadata()
        assertEquals(1000, metadata.size)
        metadata.forEachIndexed { index, metadata ->
            if (index < sampleLimit) {
                assertNotNull(metadata.sample)
                assertNotNull(metadata.sampleTimeMs)
                assertNotNull(metadata.sampleOverheadMs)
            } else {
                assertNull(metadata.sample)
                assertNotNull(metadata.sampleTimeMs)
                assertNotNull(metadata.sampleOverheadMs)
            }
        }
    }

    @Test
    fun `concurrent captureSample and retrieveSampleMetadata return consistent snapshots`() {
        val captureCount = 1050
        val readerCount = 4
        val writerDone = AtomicBoolean(false)

        // single writer: the clock is only read by captureSample(), which runs on this thread
        val writer: () -> Unit = {
            try {
                repeat(captureCount) {
                    clock.tick(10)
                    sampler.captureSample()
                }
            } finally {
                writerDone.set(true)
            }
        }
        // readers keep taking snapshots until the writer is done, so every capture races a read
        val readers = List(readerCount) {
            {
                var lastSize = 0
                do {
                    val writerFinished = writerDone.get()
                    val snapshot = sampler.retrieveSampleMetadata()
                    assertTrue(snapshot.size >= lastSize)
                    lastSize = snapshot.size
                    snapshot.forEach { assertNotNull(it) }
                    snapshot.zipWithNext().forEach { (prev, next) ->
                        assertTrue(prev.sampleTimeMs <= next.sampleTimeMs)
                    }
                } while (!writerFinished)
            }
        }

        runActionsConcurrently(listOf(writer) + readers)
        assertEquals(minOf(captureCount, 1000), sampler.retrieveSampleMetadata().size)
    }
}
