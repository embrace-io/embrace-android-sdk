package io.embrace.android.embracesdk.internal

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

internal class EmbraceSerializerTest {
    private val serializer = EmbraceSerializer()
    private val session: Session = fakeSession()
    private val payload: SessionMessage = SessionMessage(session)

    @Test
    fun testWriteToFile() {
        val result = serializer.writeToFile(payload, SessionMessage::class.java, mockk(relaxed = true))
        assertTrue(result)
    }

    @Test
    fun testLoadObject() {
        val reader = JsonReader(StringReader(Gson().toJson(payload)))
        val result = serializer.loadObject(reader, SessionMessage::class.java)
        assertEquals("fakeSessionId", result?.session?.sessionId)
    }

    @Test
    fun testBytesFromPayload() {
        val result = serializer.bytesFromPayload(payload, SessionMessage::class.java)
        assertNotNull(result)
    }
}
