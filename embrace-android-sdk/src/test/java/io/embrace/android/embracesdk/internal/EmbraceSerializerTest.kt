package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceSerializerTest {
    private val serializer = EmbraceSerializer()
    private val session: Session = fakeSession()
    private val payload: SessionMessage = SessionMessage(session)

    @Test
    fun testWriteToFile() {
        serializer.toJson(payload, SessionMessage::class.java, mockk(relaxed = true))
    }

    @Test
    fun testLoadObject() {
        val stream = serializer.toJson(payload).byteInputStream()
        val result = serializer.fromJson(stream, SessionMessage::class.java)
        assertEquals("fakeSessionId", result.session.sessionId)
    }
}
