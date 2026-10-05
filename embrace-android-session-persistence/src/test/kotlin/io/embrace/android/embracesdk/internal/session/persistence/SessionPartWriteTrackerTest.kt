package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.fakes.FakeClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SessionPartWriteTrackerTest {

    private companion object {
        private const val USER_SESSION_ID = "8CB4C22C53FEAE50D94E97B2A94E6B1E"

        // two consecutive parts of one user session: the second starts a minute after the first
        private val directory = SessionPartDirectory(
            timestamp = FakeClock.DEFAULT_FAKE_CURRENT_TIME,
            uuid = "A8BAF9242FCC7BE7020B7F533060E8EF",
            userSessionId = USER_SESSION_ID,
            sessionPartId = "53312CABDCF5A8460296A7C01013A5F9",
        )

        private val otherDirectory = SessionPartDirectory(
            timestamp = FakeClock.DEFAULT_FAKE_CURRENT_TIME + 60_000L,
            uuid = "F2C8F6640C62AF2F006929719CB0C468",
            userSessionId = USER_SESSION_ID,
            sessionPartId = "7B5F0C5B857952974FCD15B728F47289",
        )
    }

    private lateinit var tracker: SessionPartWriteTracker

    @Before
    fun setUp() {
        tracker = SessionPartWriteTracker()
    }

    @Test
    fun `directories are not written to by default`() {
        assertFalse(tracker.isWriting(directory))
    }

    @Test
    fun `a marked directory is being written to`() {
        tracker.markWriting(directory)
        assertTrue(tracker.isWriting(directory))
        assertFalse(tracker.isWriting(otherDirectory))
    }

    @Test
    fun `a completed directory is no longer being written to`() {
        tracker.markWriting(directory)
        tracker.markComplete(directory)
        assertFalse(tracker.isWriting(directory))
    }

    @Test
    fun `marking a directory repeatedly has no additional effect`() {
        tracker.markWriting(directory)
        tracker.markWriting(directory)
        tracker.markComplete(directory)
        assertFalse(tracker.isWriting(directory))
    }

    @Test
    fun `completing an unknown directory is a no-op`() {
        tracker.markWriting(directory)
        tracker.markComplete(otherDirectory)
        assertTrue(tracker.isWriting(directory))
    }
}
