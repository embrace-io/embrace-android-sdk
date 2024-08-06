package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Types
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.getSessionId
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

internal class EmbraceSerializerTest {
    private val serializer = EmbraceSerializer()
    private val payload: Envelope<SessionPayload> = fakeSessionEnvelope()

    @Test
    fun testWriteToFile() {
        val stream = ByteArrayOutputStream()
        serializer.toJson(payload, Envelope.sessionEnvelopeType, stream)
        assertTrue(stream.toByteArray().isNotEmpty())
    }

    @Test
    fun testLoadObject() {
        val stream = serializer.toJson(payload, Envelope.sessionEnvelopeType).byteInputStream()
        val result = serializer.fromJson<Envelope<SessionPayload>>(stream, Envelope.sessionEnvelopeType)
        assertEquals("fakeSessionId", result.getSessionId())
    }

    @Test
    fun testLoadListOfObjects() {
        val session1 = fakeSessionEnvelope(sessionId = "session1")
        val session2 = fakeSessionEnvelope(sessionId = "session2")
        val listOfObjects = listOf(session1, session2)
        val type = Types.newParameterizedType(List::class.java, Envelope.sessionEnvelopeType)
        val stream = serializer.toJson(listOfObjects, type).byteInputStream()
        val result = serializer.fromJson(stream, type) as List<Envelope<SessionPayload>>
        assertEquals(2, result.size)
        assertEquals(session1.getSessionId(), result[0].getSessionId())
        assertEquals(session2.getSessionId(), result[1].getSessionId())
    }

    @Test
    fun `verify truncation of stacktrace serialized JSON`() {
        val elements = Array(201) { StackTraceElement("A", "B", "C", 1) }
        val serializedString = serializer.truncatedStacktrace(elements)
        assertEquals(200, serializedString.count { it == 'A' })
        assertEquals(200, serializedString.count { it == 'B' })
        assertEquals(200, serializedString.count { it == 'C' })
        assertEquals(200, serializedString.count { it == '1' })
    }
}
