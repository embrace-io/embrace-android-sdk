package io.embrace.android.embracesdk.anr.ndk

import com.google.gson.Gson
import io.embrace.android.embracesdk.payload.NativeThreadAnrSample
import io.embrace.android.embracesdk.payload.NativeThreadAnrStackframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        assertEquals(2, sample.result)
        assertEquals(1560923409L, sample.sampleTimestamp)
        assertEquals(5L, sample.sampleDurationMs)

        val stackframe = checkNotNull(sample.stackframes?.single())
        assertEquals("0x5092afb9", stackframe.pc)
        assertEquals("0x00274fc1", stackframe.soLoadAddr)
        assertEquals("/data/foo/libtest.so", stackframe.soPath)
        assertEquals(5, stackframe.result)

        val tree = Gson().toJsonTree(sample).asJsonObject
        assertNotNull(tree)
        assertEquals(4, tree.size())
        assertEquals(2, tree.get("r").asInt)
        assertEquals(1560923409, tree.get("t").asInt)
        assertEquals(5, tree.get("d").asInt)

        val frames = tree.getAsJsonArray("s")
        assertEquals(1, frames.size())

        // assert stackframe serialized
        val frame = frames.get(0).asJsonObject
        assertEquals("0x5092afb9", frame.get("pc").asString)
        assertEquals("0x00274fc1", frame.get("l").asString)
        assertEquals("/data/foo/libtest.so", frame.get("p").asString)
        assertEquals(5, frame.get("r").asInt)
    }
}
