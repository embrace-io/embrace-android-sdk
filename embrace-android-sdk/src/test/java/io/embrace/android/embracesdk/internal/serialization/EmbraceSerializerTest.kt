package io.embrace.android.embracesdk.internal.serialization

import com.squareup.moshi.Types
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import org.junit.Assert.assertEquals
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
        assertEquals("fakeSessionId", result.session.sessionId)
    }

    @Test
    fun testLoadListOfObjects() {
        val session1 = fakeSession(sessionId = "session1")
        val session2 = fakeSession(sessionId = "session2")
        val listOfObjects = listOf(session1, session2)
        val type = Types.newParameterizedType(List::class.java, Session::class.java)
        val stream = serializer.toJson(listOfObjects, type).byteInputStream()
        val result = serializer.fromJson(stream, type) as List<Session>
        assertEquals(2, result.size)
        assertEquals(session1, result[0])
        assertEquals(session2, result[1])
    }
}
