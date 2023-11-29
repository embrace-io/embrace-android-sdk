package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.Breadcrumbs
import io.embrace.android.embracesdk.payload.DeviceInfo
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SessionMessageSerializerTest {

    private val embraceSerializer = EmbraceSerializer()

    private lateinit var msg: SessionMessage

    @Before
    fun setUp() {
        msg = SessionMessage(
            fakeSession(),
            UserInfo(),
            AppInfo(),
            DeviceInfo(),
            PerformanceInfo(),
            Breadcrumbs(),
            listOf(testSpan)
        )
    }

    @Test
    fun testSessionMessageSerializer() {
        val serializer = SessionMessageSerializer(EmbraceSerializer())

        // message should be identical to JSON.
        val expected = embraceSerializer.toJson(msg, SessionMessage::class.java)
        val observed = serializer.serialize(msg)
        assertEquals(expected, observed)

        // JSON can be generated from cache
        val cacheAttempt = serializer.serialize(msg)
        assertEquals(expected, cacheAttempt)
    }
}
