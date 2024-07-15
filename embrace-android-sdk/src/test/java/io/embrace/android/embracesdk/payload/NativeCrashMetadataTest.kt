package io.embrace.android.embracesdk.payload

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.payload.AppInfo
import io.embrace.android.embracesdk.internal.payload.DeviceInfo
import io.embrace.android.embracesdk.internal.payload.NativeCrashMetadata
import io.embrace.android.embracesdk.internal.payload.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Test

internal class NativeCrashMetadataTest {

    private val info = NativeCrashMetadata(
        AppInfo("1.0"),
        DeviceInfo("samsung"),
        UserInfo("123"),
        mapOf("key" to "value"),
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("native_crash_metadata_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<NativeCrashMetadata>("native_crash_metadata_expected.json")
        verifyInfoPopulated(obj)
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<NativeCrashMetadata>()
    }

    private fun verifyInfoPopulated(obj: NativeCrashMetadata) {
        assertEquals("1.0", obj.appInfo.appVersion)
        assertEquals("samsung", obj.deviceInfo.manufacturer)
        assertEquals("123", obj.userInfo.userId)
        assertEquals("value", checkNotNull(obj.sessionProperties)["key"])
    }
}
