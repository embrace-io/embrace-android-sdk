package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionPartSpanTest {

    @Test
    fun `fully populated session span round-trips`() {
        val sessionSpan = fullyPopulatedSessionSpanProto
        assertEquals(sessionSpan, SessionPartSpan.ADAPTER.decode(SessionPartSpan.ADAPTER.encode(sessionSpan)))
    }

    @Test
    fun `absent span stays distinct from an empty span`() {
        val absent = SessionPartSpan(span = null)
        val empty = SessionPartSpan(span = SpanProto())
        assertNull(SessionPartSpan.ADAPTER.decode(SessionPartSpan.ADAPTER.encode(absent)).span)
        assertEquals(SpanProto(), SessionPartSpan.ADAPTER.decode(SessionPartSpan.ADAPTER.encode(empty)).span)
    }

    @Test
    fun `format version survives on an otherwise empty session span`() {
        val sessionSpan = SessionPartSpan(format_version = 3)
        assertEquals(3, SessionPartSpan.ADAPTER.decode(SessionPartSpan.ADAPTER.encode(sessionSpan)).format_version)
    }

    @Test
    fun `a message holding no data at all decodes to format version zero`() {
        assertEquals(0, SessionPartSpan.ADAPTER.decode(ByteArray(0)).format_version)
    }
}
