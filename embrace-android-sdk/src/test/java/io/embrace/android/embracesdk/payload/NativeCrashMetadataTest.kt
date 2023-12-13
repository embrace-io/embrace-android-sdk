package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<NativeCrashMetadata>()
        assertNotNull(obj)
    }

    private fun verifyInfoPopulated(obj: NativeCrashMetadata) {
        assertEquals("1.0", obj.appInfo.appVersion)
        assertEquals("samsung", obj.deviceInfo.manufacturer)
        assertEquals("123", obj.userInfo.userId)
        assertEquals("value", checkNotNull(obj.sessionProperties)["key"])
    }
}
