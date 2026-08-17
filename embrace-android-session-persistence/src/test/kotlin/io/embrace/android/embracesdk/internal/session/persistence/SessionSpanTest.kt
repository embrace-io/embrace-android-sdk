package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionSpanTest {

    @Test
    fun `fully populated session span round-trips`() {
        val sessionSpan = fullyPopulatedSessionSpanProto
        assertEquals(sessionSpan, SessionSpan.ADAPTER.decode(SessionSpan.ADAPTER.encode(sessionSpan)))
    }

    @Test
    fun `absent span stays distinct from an empty span`() {
        val absent = SessionSpan(span = null)
        val empty = SessionSpan(span = SpanProto())
        assertNull(SessionSpan.ADAPTER.decode(SessionSpan.ADAPTER.encode(absent)).span)
        assertEquals(SpanProto(), SessionSpan.ADAPTER.decode(SessionSpan.ADAPTER.encode(empty)).span)
    }

    @Test
    fun `format version survives on an otherwise empty session span`() {
        val sessionSpan = SessionSpan(format_version = 3)
        assertEquals(3, SessionSpan.ADAPTER.decode(SessionSpan.ADAPTER.encode(sessionSpan)).format_version)
    }

    @Test
    fun `a message holding no data at all decodes to format version zero`() {
        assertEquals(0, SessionSpan.ADAPTER.decode(ByteArray(0)).format_version)
    }
}
