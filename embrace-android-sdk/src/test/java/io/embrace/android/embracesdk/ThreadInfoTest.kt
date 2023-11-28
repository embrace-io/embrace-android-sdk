package io.embrace.android.embracesdk

import com.google.gson.Gson
import io.embrace.android.embracesdk.payload.ThreadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class ThreadInfoTest {

    @Test
    fun testThreadInfoMaxLines() {
        val threadInfo = ThreadInfo.ofThread(
            Thread.currentThread(),
            arrayOf(
                StackTraceElement("Foo", "bar", "Foo.kt", 5),
                StackTraceElement("Foo", "wham", "Foo.kt", 27)
            ),
            1
        )
        assertEquals(1, checkNotNull(threadInfo.lines).size)
    }

    @Test
    fun testThreadInfoSerialization() {
        val threadInfo = ThreadInfo(
            13, Thread.State.RUNNABLE, "my-thread", 5,
            listOf(
                "java.base/java.lang.Thread.getStackTrace(Thread.java:1602)",
                "io.embrace.android.embracesdk.ThreadInfoTest.testThreadInfoSerialization(ThreadInfoTest.kt:18)"
            )
        )

        val expectedInfo = ResourceReader.readResourceAsText("thread_info_expected.json")
            .filter { !it.isWhitespace() }

        val observed = Gson().toJson(threadInfo)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testThreadInfoDeserialization() {
        val json = ResourceReader.readResourceAsText("thread_info_expected.json")
        val obj = Gson().fromJson(json, ThreadInfo::class.java)
        assertEquals(13, obj.threadId)
        assertEquals(5, obj.priority)
        assertEquals(Thread.State.RUNNABLE, obj.state)
        assertEquals("my-thread", obj.name)
        assertEquals(
            listOf(
                "java.base/java.lang.Thread.getStackTrace(Thread.java:1602)",
                "io.embrace.android.embracesdk.ThreadInfoTest.testThreadInfoSerialization(ThreadInfoTest.kt:18)"
            ),
            obj.lines
        )
    }

    @Test
    fun testThreadInfoEmptyObject() {
        val threadInfo = Gson().fromJson("{}", ThreadInfo::class.java)
        assertNotNull(threadInfo)
    }
}
