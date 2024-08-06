package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class SessionIdTrackerImplTest {

    private lateinit var tracker: SessionIdTracker

    @Before
    fun setUp() {
        tracker = SessionIdTrackerImpl(null, EmbLoggerImpl())
    }

    @Test
    fun `test set session id`() {
        var id: String? = null
        tracker.addListener {
            id = it
        }
        assertNull(tracker.getActiveSession())
        assertNull(tracker.getActiveSessionId())
        assertNull(id)

        tracker.setActiveSession("123", true)
        assertEquals(SessionData("123", true), tracker.getActiveSession())
        assertEquals("123", tracker.getActiveSessionId())
        assertEquals("123", id)

        tracker.setActiveSession("456", true)
        assertEquals("456", id)

        tracker.setActiveSession(null, false)
        assertEquals(null, id)
    }
}
