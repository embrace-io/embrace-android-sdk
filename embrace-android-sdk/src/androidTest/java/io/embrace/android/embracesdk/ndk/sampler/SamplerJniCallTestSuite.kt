package io.embrace.android.embracesdk.ndk.sampler

import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerNdkDelegate
import io.embrace.android.embracesdk.ndk.NativeTestSuite
import io.embrace.android.embracesdk.payload.NativeThreadAnrSample
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SamplerJniCallTestSuite : NativeTestSuite() {

    private val delegate = NativeThreadSamplerNdkDelegate()

    external fun run()

    @Test
    fun testNoSamples() {
        assertEquals(emptyList<NativeThreadAnrSample>(), delegate.finishSampling())
    }

    @Test
    fun testSamplesSerialized() {
        run()
        val samples = checkNotNull(delegate.finishSampling())
        assertEquals(3, samples.size)
        verifySampleCaptured(samples[0], 0x12345678)
        verifySampleCaptured(samples[1], 0x12300000)
        verifySampleCaptured(samples[2], 0x10090000)
    }

    private fun verifySampleCaptured(sample: NativeThreadAnrSample, pc: Int) {
        assertEquals(0, sample.result)
        assertEquals(9L, sample.sampleDurationMs)
        assertEquals(1500000000L, sample.sampleTimestamp)

        val frames = checkNotNull(sample.stackframes)
        val frame = frames.single()
        assertEquals(1, frames.size)
        assertEquals("0x" + pc.toString(16), frame.pc)
        assertEquals("0x" + 0x87654321.toString(16), frame.soLoadAddr)
        assertEquals(0, frame.result)
        assertEquals("libtest.so", frame.soPath)
    }
}
