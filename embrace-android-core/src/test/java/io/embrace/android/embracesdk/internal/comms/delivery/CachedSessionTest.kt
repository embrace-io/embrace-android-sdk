package io.embrace.android.embracesdk.internal.comms.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class CachedSessionTest {

    @Test
    fun `create cached session`() {
        assertEquals(
            "last_session.5000.sessionId.json",
            CachedSession.create("sessionId", 5000, false).filename
        )
        assertEquals(
            "last_session.5000.sessionId.v2.json",
            CachedSession.create("sessionId", 5000, true).filename
        )
    }

    @Test
    fun `decode filename`() {
        val v1Session = checkNotNull(CachedSession.fromFilename("last_session.5000.sessionId.json"))
        assertEquals("sessionId", v1Session.sessionId)
        assertEquals(5000, v1Session.timestampMs)
        assertEquals("last_session.5000.sessionId.json", v1Session.filename)
        assertFalse(v1Session.v2Payload)

        val v2Session = checkNotNull(CachedSession.fromFilename("last_session.5000.sessionId.v2.json"))
        assertEquals("sessionId", v2Session.sessionId)
        assertEquals(5000, v2Session.timestampMs)
        assertEquals("last_session.5000.sessionId.v2.json", v2Session.filename)
        assertTrue(v2Session.v2Payload)

        val invalidSession = CachedSession.fromFilename("myfoo.json")
        assertNull(invalidSession)
    }
}
