package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

internal class EmbraceSerializerTest {
    private val serializer = EmbraceSerializer()
    private val session: Session = fakeSession()
    private val payload: SessionMessage = SessionMessage(session)

    @Test
    fun testWriteToFile() {
        val stream = ByteArrayOutputStream()
        serializer.toJson(payload, SessionMessage::class.java, stream)
        assertTrue(stream.toByteArray().isNotEmpty())
    }

    @Test
    fun testLoadObject() {
        val stream = serializer.toJson(payload).byteInputStream()
        val result = serializer.fromJson(stream, SessionMessage::class.java)
        Assert.assertEquals("fakeSessionId", result.session.sessionId)
    }
}
