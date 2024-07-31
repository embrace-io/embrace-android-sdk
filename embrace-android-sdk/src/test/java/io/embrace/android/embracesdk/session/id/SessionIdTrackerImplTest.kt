package io.embrace.android.embracesdk.session.id

import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.id.SessionIdTrackerImpl
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
        assertNull(tracker.getActiveSessionId())
        assertNull(id)

        tracker.setActiveSessionId("123", true)
        assertEquals("123", tracker.getActiveSessionId())
        assertEquals("123", id)

        tracker.setActiveSessionId("456", true)
        assertEquals("456", id)

        tracker.setActiveSessionId(null, false)
        assertEquals(null, id)
    }
}
