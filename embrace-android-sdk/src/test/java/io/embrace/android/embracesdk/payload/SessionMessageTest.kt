package io.embrace.android.embracesdk.payload

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fixtures.testSpanSnapshot
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.toOldPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SessionMessageTest {

    private val session = fakeSession()
    private val spans = listOf(
        EmbraceSpanData(
            "fake-span-id",
            "",
            null,
            "",
            0,
            0,
            StatusCode.OK,
            emptyList()
        )
    )

    private val spanSnapshots = listOfNotNull(testSpanSnapshot.toOldPayload())

    private val info = SessionMessage(
        session,
        spans,
        spanSnapshots
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("session_message_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<SessionMessage>("session_message_expected.json")
        assertNotNull(obj)
        assertEquals(session, obj.session)
        assertEquals(spans, obj.spans)
        assertEquals(spanSnapshots, obj.spanSnapshots)
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<SessionMessage>()
    }

    @Test
    fun `is v2 payload`() {
        assertFalse(info.isV2Payload())
        val obj = SessionMessage(session = session, data = SessionPayload())
        assertTrue(obj.isV2Payload())
    }
}
