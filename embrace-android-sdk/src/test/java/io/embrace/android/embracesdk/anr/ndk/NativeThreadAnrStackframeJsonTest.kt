package io.embrace.android.embracesdk.anr.ndk

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.payload.NativeThreadAnrStackframe
import org.junit.Assert.assertEquals
import org.junit.Test

internal class NativeThreadAnrStackframeJsonTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testSerialization() {
        val frame = NativeThreadAnrStackframe(
            "0x5092afb9",
            "0x00274fc1",
            "/data/foo/libtest.so",
            11
        )

        val expected = ResourceReader.readResourceAsText("native_thread_anr_stackframe.json")
            .filter { !it.isWhitespace() }

        val json = serializer.toJson(frame)
        assertEquals(expected, json)
    }
}
