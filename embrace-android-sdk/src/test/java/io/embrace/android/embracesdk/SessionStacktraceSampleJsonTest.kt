package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.internal.EmbraceSerializer
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

    private val serializer = EmbraceSerializer()

    @Test
    fun testSymbolSerialization() {
        val session = fakeSession().copy(symbols = mapOf("foo" to "bar"))
        val json = serializer.toJson(session)
        val expected = ResourceReader.readResourceAsText("session_stacktrace_symbols.json")
            .filter { !it.isWhitespace() }
        assertEquals(expected, json)
    }

    @Test
    fun testSerialization() {
        val fixture = generateNativeSampleTick()
        val session = PerformanceInfo(
            nativeThreadAnrIntervals = listOf(fixture)
        )
        val json = serializer.toJson(session)
        val expected = ResourceReader.readResourceAsText("session_native_stacktrace.json")
            .filter { !it.isWhitespace() }
        assertEquals(expected, json)
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
}
