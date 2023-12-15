package io.embrace.android.embracesdk.anr

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.payload.AnrInterval
import io.embrace.android.embracesdk.payload.AnrSample
import io.embrace.android.embracesdk.payload.AnrSampleList
import io.embrace.android.embracesdk.payload.ThreadInfo
import io.embrace.android.embracesdk.payload.extensions.clearSamples
import io.embrace.android.embracesdk.payload.extensions.deepCopy
import io.embrace.android.embracesdk.payload.extensions.duration
import io.embrace.android.embracesdk.payload.extensions.size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

internal class AnrIntervalTest {

    private val threadInfo = ThreadInfo(
        13,
        Thread.State.RUNNABLE,
        "my-thread",
        5,
        listOf(
            "java.base/java.lang.Thread.getStackTrace(Thread.java:1602)",
            "io.embrace.android.embracesdk.ThreadInfoTest.testThreadInfoSerialization(ThreadInfoTest.kt:18)"
        )
    )

    private val anrSample = AnrSample(150980980980, listOf(threadInfo), 0)

    private val anrSampleList = AnrSampleList(listOf(anrSample))

    private val interval = AnrInterval(
        startTime = 150980980980,
        endTime = 150980980980 + 5000,
        lastKnownTime = 150980980980 + 4000,
        type = AnrInterval.Type.UI,
        anrSampleList = anrSampleList,
        code = AnrInterval.CODE_SAMPLES_CLEARED
    )

    @Test
    fun testClearAnrSamples() {
        val interval = interval.copy(code = AnrInterval.CODE_DEFAULT)
        assertEquals(1, interval.size())
        assertEquals(AnrInterval.CODE_DEFAULT, interval.code)

        val copy = interval.clearSamples()
        assertEquals(1, interval.size())
        assertEquals(AnrInterval.CODE_DEFAULT, interval.code)
        assertNull(copy.anrSampleList)
        assertEquals(AnrInterval.CODE_SAMPLES_CLEARED, copy.code)
    }

    @Test
    fun testAnrTickSerialization() {
        assertJsonMatchesGoldenFile("anr_interval_expected.json", interval.copy())
    }

    @Test
    fun testAnrTickDeserialization() {
        val obj = deserializeJsonFromResource<AnrInterval>("anr_interval_expected.json")
        assertEquals(150980980980, obj.startTime)
        assertEquals(150980980980 + 5000, obj.endTime)
        assertEquals(150980980980 + 4000, obj.lastKnownTime)
        assertEquals(AnrInterval.Type.UI, obj.type)
        assertEquals(anrSampleList, obj.anrSampleList)
    }

    @Test
    fun testAnrIntervalEmptyObject() {
        val anrInterval = deserializeEmptyJsonString<AnrInterval>()
        assertNotNull(anrInterval)
    }

    @Test
    fun testDeepCopy() {
        val deepCopy = interval.deepCopy()
        assertEquals(interval.startTime, deepCopy.startTime)
        assertEquals(interval.endTime, deepCopy.endTime)
        assertEquals(interval.lastKnownTime, deepCopy.lastKnownTime)
        assertEquals(interval.type, deepCopy.type)
        assertEquals(interval.code, deepCopy.code)
        assertEquals(interval.anrSampleList, deepCopy.anrSampleList)
        assertNotSame(interval.anrSampleList, deepCopy.anrSampleList)
    }

    @Test
    fun testDuration() {
        assertEquals(
            5000L,
            AnrInterval(startTime = 1600000000, endTime = 1600005000).duration()
        )
        assertEquals(
            5000L,
            AnrInterval(startTime = 1600000000, lastKnownTime = 1600005000).duration()
        )
        assertEquals(
            5000L,
            AnrInterval(
                startTime = 1600000000,
                endTime = 1600005000,
                lastKnownTime = 1600003000
            ).duration()
        )
        assertEquals(-1L, AnrInterval(startTime = 1600000000).duration())
    }
}
