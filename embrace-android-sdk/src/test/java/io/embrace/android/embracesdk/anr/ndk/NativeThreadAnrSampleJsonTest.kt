package io.embrace.android.embracesdk.anr.ndk

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.payload.NativeThreadAnrSample
import io.embrace.android.embracesdk.payload.NativeThreadAnrStackframe
import org.junit.Test

internal class NativeThreadAnrSampleJsonTest {

    @Test
    fun testSerialization() {
        val sample = NativeThreadAnrSample(
            2,
            1560923409,
            5,
            listOf(
                NativeThreadAnrStackframe(
                    "0x5092afb9",
                    "0x00274fc1",
                    "/data/foo/libtest.so",
                    5
                )
            )
        )
        assertJsonMatchesGoldenFile("native_thread_anr_sample.json", sample)
    }
}
