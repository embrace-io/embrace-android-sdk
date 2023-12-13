package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.Breadcrumbs
import io.embrace.android.embracesdk.payload.DeviceInfo
import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.UserInfo
import io.embrace.android.embracesdk.payload.ViewBreadcrumb
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
            UserInfo("fakeUserId", "fakeUserName", "fakeUserEmail"),
            AppInfo("1.0",),
            DeviceInfo("google"),
            PerformanceInfo(DiskUsage(1509609900020L, 1509609900020L)),
            Breadcrumbs(listOf(ViewBreadcrumb("fakeViewName", 1509609900020L))),
            listOf(testSpan)
        )
    }

    @Test
    fun testSessionMessageSerializer() {
        val serializer = SessionMessageSerializer(EmbraceSerializer())

        // message should be identical to JSON.
        val expected = embraceSerializer.toJson(msg)
        val observed = serializer.serialize(msg)
        assertEquals(expected, observed)

        // JSON can be generated from cache
        val cacheAttempt = serializer.serialize(msg)
        assertEquals(expected, cacheAttempt)

        // cleaning collections does not alter generated json
        serializer.cleanCollections()
        val cleanAttempt = serializer.serialize(msg)
        assertEquals(expected, cleanAttempt)
    }
}
