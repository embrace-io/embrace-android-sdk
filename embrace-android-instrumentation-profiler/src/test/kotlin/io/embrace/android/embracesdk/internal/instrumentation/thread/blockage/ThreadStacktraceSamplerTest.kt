package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
            200
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
}
