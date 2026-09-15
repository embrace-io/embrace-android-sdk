package io.embrace.android.embracesdk.internal.utils

import io.embrace.android.embracesdk.fakes.FakeSectionRecorder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class FileWriteCountersTest {

    private lateinit var recorder: FakeSectionRecorder
    private lateinit var counters: FileWriteCounters

    @Before
    fun setUp() {
        recorder = FakeSectionRecorder()
        SystemTrace.recorder = recorder
        counters = FileWriteCounters("bytes-written", "files-written")
    }

    @After
    fun tearDown() {
        SystemTrace.recorder = null
    }

    @Test
    fun `nothing is published until a write is recorded`() {
        assertTrue(recorder.counters.isEmpty())
    }

    @Test
    fun `each write raises the byte total by its size and the file total by one`() {
        counters.recordWrite(100)
        counters.recordWrite(20)
        assertEquals(listOf(100L, 120L), recorder.counters["bytes-written"])
        assertEquals(listOf(1L, 2L), recorder.counters["files-written"])
    }

    @Test
    fun `a write of nothing still counts as a write`() {
        counters.recordWrite(0)
        assertEquals(0L, recorder.latestCounter("bytes-written"))
        assertEquals(1L, recorder.latestCounter("files-written"))
    }
}
