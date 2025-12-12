package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class SessionTrackerImplTest {

    private lateinit var tracker: SessionTracker

    @Before
    fun setUp() {
        tracker = SessionTrackerImpl(null, EmbLoggerImpl())
    }

    @Test
    fun `test set session id`() {
        var count = 0
        tracker.addListener {
            count++
        }
        assertNull(tracker.getActiveSession())
        assertNull(tracker.getActiveSessionId())
        assertEquals(0, count)

//        tracker.setActiveSession("123", AppState.FOREGROUND)
//        assertEquals(SessionData("123", AppState.FOREGROUND), tracker.getActiveSession())
//        assertEquals("123", tracker.getActiveSessionId())
//        assertEquals(1, count)
//
//        tracker.setActiveSession("456", AppState.FOREGROUND)
//        assertEquals(2, count)
//
//        tracker.setActiveSession(null, AppState.BACKGROUND)
//        assertEquals(3, count)
    }
}
