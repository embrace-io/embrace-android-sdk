package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.mockk.mockk
import org.junit.Assert
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
        Assert.assertEquals("fakeSessionId", result.session.sessionId)
    }
}
