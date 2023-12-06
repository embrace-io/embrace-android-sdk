package io.embrace.android.embracesdk.anr.ndk

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.payload.NativeThreadAnrSample
import io.embrace.android.embracesdk.payload.NativeThreadAnrStackframe
import org.junit.Assert.assertEquals
import org.junit.Test

internal class NativeThreadAnrSampleJsonTest {

    private val serializer = EmbraceSerializer()

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

        val expected = ResourceReader.readResourceAsText("native_thread_anr_sample.json")
            .filter { !it.isWhitespace() }
        val json = serializer.toJson(sample)
        assertEquals(expected, json)
    }
}
