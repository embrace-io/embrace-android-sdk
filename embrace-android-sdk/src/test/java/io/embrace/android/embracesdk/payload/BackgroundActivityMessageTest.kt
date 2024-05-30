package io.embrace.android.embracesdk.payload

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.fakeBackgroundActivity
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class BackgroundActivityMessageTest {

    private val backgroundActivity = fakeBackgroundActivity()
    private val appInfo = AppInfo("fake-app-id")
    private val deviceInfo = DeviceInfo("fake-manufacturer")
    private val spans = listOf(EmbraceSpanData("fake-span-id", "", "", "", 0, 0, StatusCode.OK))
    private val perfInfo = PerformanceInfo(DiskUsage(1, 2))

    private val info = SessionMessage(
        backgroundActivity,
        appInfo,
        deviceInfo,
        perfInfo,
        spans
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("bg_activity_message_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<SessionMessage>("bg_activity_message_expected.json")
        assertNotNull(obj)

        assertEquals(backgroundActivity.startTime, obj.session.startTime)
        assertEquals(appInfo, obj.appInfo)
        assertEquals(deviceInfo, obj.deviceInfo)
        assertEquals(perfInfo, obj.performanceInfo)
        assertEquals(spans, obj.spans)
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<SessionMessage>()
    }
}
