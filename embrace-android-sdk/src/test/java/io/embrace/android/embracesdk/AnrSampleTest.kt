package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.AnrSample
import io.embrace.android.embracesdk.payload.ThreadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class AnrSampleTest {

    private val threadInfo = ThreadInfo(
        13, Thread.State.RUNNABLE, "my-thread", 5,
        listOf(
            "java.base/java.lang.Thread.getStackTrace(Thread.java:1602)",
            "io.embrace.android.embracesdk.ThreadInfoTest.testThreadInfoSerialization(ThreadInfoTest.kt:18)"
        )
    )

    @Test
    fun testAnrTickSerialization() {
        val obj = AnrSample(156098234092, listOf(threadInfo), 2)
        assertJsonMatchesGoldenFile("anr_tick_expected.json", obj)
    }

    @Test
    fun testAnrTickDeserialization() {
        val obj = deserializeJsonFromResource<AnrSample>("anr_tick_expected.json")
        assertEquals(2L, obj.sampleOverheadMs)
        assertEquals(156098234092, obj.timestamp)
        assertEquals(listOf(threadInfo), obj.threads)
    }

    @Test
    fun testThreadInfoEmptyObject() {
        val obj = deserializeEmptyJsonString<AnrSample>()
        assertNotNull(obj)
    }
}
