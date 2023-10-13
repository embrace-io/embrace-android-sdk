package io.embrace.android.embracesdk.anr.ndk

import com.google.gson.Gson
import io.embrace.android.embracesdk.payload.NativeThreadAnrStackframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        val tree = Gson().toJsonTree(frame).asJsonObject
        assertNotNull(tree)
        assertEquals(4, tree.size())
        assertEquals("0x5092afb9", tree.get("pc").asString)
        assertEquals("0x00274fc1", tree.get("l").asString)
        assertEquals("/data/foo/libtest.so", tree.get("p").asString)
        assertEquals(11, tree.get("r").asInt)
    }
}
