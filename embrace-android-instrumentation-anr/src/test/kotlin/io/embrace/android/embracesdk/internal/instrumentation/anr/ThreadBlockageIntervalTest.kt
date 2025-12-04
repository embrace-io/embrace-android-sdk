package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.ThreadBlockageInterval
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.ThreadBlockageSample
import io.embrace.android.embracesdk.internal.payload.ThreadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

internal class ThreadBlockageIntervalTest {

    private val threadInfo = ThreadInfo(
        13,
        Thread.State.RUNNABLE,
        "my-thread",
        5,
        listOf(
            "java.base/java.lang.Thread.getStackTrace(Thread.java:1602)",
            "io.embrace.android.embracesdk.ThreadInfoTest.testThreadInfoSerialization(ThreadInfoTest.kt:18)"
        ),
        2
    )

    private val threadBlockageSample = ThreadBlockageSample(150980980980, listOf(threadInfo), 0)

    private val sampleList = listOf(threadBlockageSample)

    private val interval = ThreadBlockageInterval(
        startTime = 150980980980,
        endTime = 150980980980 + 5000,
        lastKnownTime = 150980980980 + 4000,
        samples = sampleList,
        code = ThreadBlockageInterval.CODE_SAMPLES_CLEARED
    )

    @Test
    fun testClearSamples() {
        val interval = interval.copy(code = ThreadBlockageInterval.CODE_DEFAULT)
        assertEquals(1, interval.size())
        assertEquals(ThreadBlockageInterval.CODE_DEFAULT, interval.code)

        val copy = interval.clearSamples()
        assertEquals(1, interval.size())
        assertEquals(ThreadBlockageInterval.CODE_DEFAULT, interval.code)
        assertNull(copy.samples)
        assertEquals(ThreadBlockageInterval.CODE_SAMPLES_CLEARED, copy.code)
    }

    @Test
    fun testDeepCopy() {
        val deepCopy = interval.deepCopy()
        assertEquals(interval.startTime, deepCopy.startTime)
        assertEquals(interval.endTime, deepCopy.endTime)
        assertEquals(interval.lastKnownTime, deepCopy.lastKnownTime)
        assertEquals(interval.code, deepCopy.code)
        assertEquals(interval.samples, deepCopy.samples)
        assertNotSame(interval.samples, deepCopy.samples)
    }

    @Test
    fun testDuration() {
        assertEquals(
            5000L,
            ThreadBlockageInterval(startTime = 1600000000, endTime = 1600005000).duration()
        )
        assertEquals(
            5000L,
            ThreadBlockageInterval(startTime = 1600000000, lastKnownTime = 1600005000).duration()
        )
        assertEquals(
            5000L,
            ThreadBlockageInterval(
                startTime = 1600000000,
                endTime = 1600005000,
                lastKnownTime = 1600003000
            ).duration()
        )
        assertEquals(-1L, ThreadBlockageInterval(startTime = 1600000000).duration())
    }
}
