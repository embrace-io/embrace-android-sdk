package io.embrace.android.embracesdk

import com.google.gson.Gson
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
        val expectedInfo = ResourceReader.readResourceAsText("anr_tick_expected.json")
            .filter { !it.isWhitespace() }

        val obj = AnrSample(156098234092, listOf(threadInfo), 2)
        val observed = Gson().toJson(obj)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testAnrTickDeserialization() {
        val json = ResourceReader.readResourceAsText("anr_tick_expected.json")
        val obj = Gson().fromJson(json, AnrSample::class.java)
        assertEquals(2L, obj.sampleOverheadMs)
        assertEquals(156098234092, obj.timestamp)
        assertEquals(listOf(threadInfo), obj.threads)
    }

    @Test
    fun testThreadInfoEmptyObject() {
        val threadInfo = Gson().fromJson("{}", AnrSample::class.java)
        assertNotNull(threadInfo)
    }
}
