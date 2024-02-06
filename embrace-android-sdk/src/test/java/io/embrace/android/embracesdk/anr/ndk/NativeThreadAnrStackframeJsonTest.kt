package io.embrace.android.embracesdk.anr.ndk

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.payload.NativeThreadAnrStackframe
import org.junit.Test

internal class NativeThreadAnrStackframeJsonTest {

    @Test
    fun testSerialization() {
        val frame = NativeThreadAnrStackframe(
            "0x5092afb9",
            "0x00274fc1",
            "/data/foo/libtest.so",
            11
        )
        assertJsonMatchesGoldenFile("native_thread_anr_stackframe.json", frame)
    }
}
