package io.embrace.android.embracesdk

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.payload.NativeThreadAnrInterval
import io.embrace.android.embracesdk.payload.NativeThreadAnrSample
import io.embrace.android.embracesdk.payload.NativeThreadAnrStackframe
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.ThreadState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the stacktrace sample is included in the session JSON when set
 */
internal class SessionStacktraceSampleJsonTest {

    @Test
    fun testSymbolSerialization() {
        val session = fakeSession().copy(symbols = mapOf("foo" to "bar"))
        val root = Gson().toJsonTree(session).asJsonObject

        // assert symbols included
        val symbols = root.getAsJsonObject("sb")
        assertEquals("bar", symbols["foo"].asString)
    }

    @Test
    fun testSerialization() {
        val fixture = generateNativeSampleTick()
        val session = PerformanceInfo(
            nativeThreadAnrIntervals = listOf(fixture)
        )
        val root = Gson().toJsonTree(session).asJsonObject

        // assert ticks included
        val ticks = root.getAsJsonArray("nst")
        val tick = ticks.single() as JsonObject

        // assert tick info serialized
        verifyTickInfoJson(tick)

        // assert stacktrace sample serialized
        verifyStacktraceSampleJson(tick.getAsJsonArray("ss"))
    }

    private fun generateNativeSampleTick(): NativeThreadAnrInterval {
        val obj = NativeThreadAnrSample(
            2,
            15002000,
            2,
            listOf(
                NativeThreadAnrStackframe(
                    "0x5092afb9",
                    "0x00274fc1",
                    "/data/foo/libtest.so",
                    5
                )
            )
        )
        return NativeThreadAnrInterval(
            25,
            "UnityMain",
            5,
            100,
            15000000,
            mutableListOf(obj),
            ThreadState.RUNNABLE,
            AnrRemoteConfig.Unwinder.LIBUNWIND
        )
    }

    private fun verifyTickInfoJson(node: JsonObject) {
        assertEquals(8, node.size())
        assertEquals(100L, node.get("os").asLong)
        assertEquals(15000000L, node.get("t").asLong)
        assertEquals(25, node.get("id").asInt)
        assertEquals("UnityMain", node.get("n").asString)
        assertEquals(ThreadState.RUNNABLE.code, node.get("s").asInt)
        assertEquals(AnrRemoteConfig.Unwinder.LIBUNWIND.code, node.get("uw").asInt)
        assertEquals(5, node.get("p").asInt)
    }

    private fun verifyStacktraceSampleJson(array: JsonArray) {
        val node = array.single() as JsonObject
        assertEquals(4, node.size())
        assertEquals(2, node.get("r").asInt)
        assertEquals(15002000L, node.get("t").asLong)
        assertEquals(2, node.get("d").asInt)

        val frames = node.get("s").asJsonArray
        assertEquals(1, frames.size())

        // assert stackframe serialized
        val frame = frames.get(0).asJsonObject
        assertEquals("0x5092afb9", frame.get("pc").asString)
        assertEquals("0x00274fc1", frame.get("l").asString)
        assertEquals("/data/foo/libtest.so", frame.get("p").asString)
        assertEquals(5, frame.get("r").asInt)
    }
}
