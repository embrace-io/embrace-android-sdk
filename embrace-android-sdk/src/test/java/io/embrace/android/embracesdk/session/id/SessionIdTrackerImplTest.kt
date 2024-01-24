package io.embrace.android.embracesdk.session.id

import io.embrace.android.embracesdk.FakeNdkService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class SessionIdTrackerImplTest {

    private lateinit var tracker: SessionIdTracker
    private lateinit var ndkService: FakeNdkService

    @Before
    fun setUp() {
        tracker = SessionIdTrackerImpl()
        ndkService = FakeNdkService()
    }

    @Test
    fun `test set session id`() {
        assertNull(tracker.getActiveSessionId())
        tracker.setActiveSessionId("123", true)
        assertEquals("123", tracker.getActiveSessionId())

        tracker.ndkService = ndkService
        assertEquals("123", ndkService.sessionId)

        tracker.setActiveSessionId("456", true)
        assertEquals("456", ndkService.sessionId)

        tracker.setActiveSessionId(null, false)
        assertEquals("", ndkService.sessionId)
    }
}
