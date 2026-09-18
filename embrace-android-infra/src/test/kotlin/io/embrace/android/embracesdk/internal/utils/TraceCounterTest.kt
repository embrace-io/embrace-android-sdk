package io.embrace.android.embracesdk.internal.utils

import io.embrace.android.embracesdk.fakes.FakeSectionRecorder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class TraceCounterTest {

    private lateinit var recorder: FakeSectionRecorder

    @Before
    fun setUp() {
        recorder = FakeSectionRecorder()
        SystemTrace.recorder = recorder
    }

    @After
    fun tearDown() {
        SystemTrace.recorder = null
    }

    @Test
    fun `each addition publishes the running total`() {
        val counter = TraceCounter("bytes")
        counter.add(10)
        counter.add(5)
        counter.add(0)
        assertEquals(listOf(10L, 15L, 15L), recorder.counters["bytes"])
    }

    @Test
    fun `counters are published under the name given, unprefixed`() {
        TraceCounter("files").add(1)
        assertEquals(setOf("files"), recorder.counters.keys)
    }

    @Test
    fun `the total is kept while nothing is capturing, so a later capture sees the real total`() {
        SystemTrace.recorder = null
        val counter = TraceCounter("bytes")
        counter.add(7)

        SystemTrace.recorder = recorder
        counter.add(3)

        assertEquals(listOf(10L), recorder.counters["bytes"])
    }

    @Test
    fun `separate counters keep separate totals`() {
        TraceCounter("a").add(1)
        TraceCounter("b").add(2)

        assertEquals(1L, recorder.latestCounter("a"))
        assertEquals(2L, recorder.latestCounter("b"))
        assertNull(recorder.latestCounter("c"))
    }
}
