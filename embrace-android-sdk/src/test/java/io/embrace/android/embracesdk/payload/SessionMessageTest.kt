package io.embrace.android.embracesdk.payload

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class SessionMessageTest {

    private val session = fakeSession()
    private val userInfo = UserInfo("fake-user-id", "fake-user-name")
    private val appInfo = AppInfo("fake-app-version")
    private val deviceInfo = DeviceInfo("fake-manufacturer")
    private val performanceInfo = PerformanceInfo(DiskUsage(150923409L, 509209823L))
    private val breadcrumbs = Breadcrumbs(
        customBreadcrumbs = listOf(CustomBreadcrumb("Hi", 109234098234))
    )
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

    private val info = SessionMessage(
        session,
        userInfo,
        appInfo,
        deviceInfo,
        performanceInfo,
        breadcrumbs,
        spans
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
        assertEquals(userInfo, obj.userInfo)
        assertEquals(appInfo, obj.appInfo)
        assertEquals(deviceInfo, obj.deviceInfo)
        assertEquals(performanceInfo, obj.performanceInfo)
        assertEquals(breadcrumbs, obj.breadcrumbs)
        assertEquals(spans, obj.spans)
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<SessionMessage>()
    }
}
