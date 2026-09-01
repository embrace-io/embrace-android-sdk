package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SessionPartWriteTrackerTest {

    private companion object {
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        private const val OTHER_SESSION_PART_ID = "cccccccccccccccccccccccccccccccc"
    }

    private lateinit var tracker: SessionPartWriteTracker

    @Before
    fun setUp() {
        tracker = SessionPartWriteTracker()
    }

    @Test
    fun `session parts are not written to by default`() {
        assertFalse(tracker.isWriting(SESSION_PART_ID))
    }

    @Test
    fun `a marked session part is being written to`() {
        tracker.markWriting(SESSION_PART_ID)
        assertTrue(tracker.isWriting(SESSION_PART_ID))
        assertFalse(tracker.isWriting(OTHER_SESSION_PART_ID))
    }

    @Test
    fun `a completed session part is no longer being written to`() {
        tracker.markWriting(SESSION_PART_ID)
        tracker.markComplete(SESSION_PART_ID)
        assertFalse(tracker.isWriting(SESSION_PART_ID))
    }

    @Test
    fun `marking a session part repeatedly has no additional effect`() {
        tracker.markWriting(SESSION_PART_ID)
        tracker.markWriting(SESSION_PART_ID)
        tracker.markComplete(SESSION_PART_ID)
        assertFalse(tracker.isWriting(SESSION_PART_ID))
    }

    @Test
    fun `completing an unknown session part is a no-op`() {
        tracker.markWriting(SESSION_PART_ID)
        tracker.markComplete(OTHER_SESSION_PART_ID)
        assertTrue(tracker.isWriting(SESSION_PART_ID))
    }
}
