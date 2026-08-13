package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SectionDurationTrackerTest {

    private lateinit var tracker: SectionDurationTracker

    @Before
    fun setUp() {
        tracker = SectionDurationTracker()
    }

    @Test
    fun `record keeps the first duration for repeated sections`() {
        tracker.record("x", 2)
        tracker.record("x", 5)
        tracker.record("y", 1)
        assertEquals(mapOf("x" to 2L, "y" to 1L), tracker.flush())
    }

    @Test
    fun `flush disables recording and clears state`() {
        tracker.record("x", 2)
        assertEquals(mapOf("x" to 2L), tracker.flush())
        tracker.record("x", 2)
        assertTrue(tracker.flush().isEmpty())
    }

    @Test
    fun `reset re-enables recording with cleared state`() {
        tracker.record("x", 2)
        tracker.flush()
        tracker.reset()
        tracker.record("y", 3)
        assertEquals(mapOf("y" to 3L), tracker.flush())
    }
}
