package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.DeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DeviceInfoTest {

    private val info = DeviceInfo(
        "samsung",
        "S20", "armeabi", false,
        "en-US",
        150982302,
        "android",
        "10.2.1",
        29,
        "1080x720",
        "GMT+1",
        8
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("device_info_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<DeviceInfo>("device_info_expected.json")
        assertEquals("samsung", obj.manufacturer)
        assertEquals("S20", obj.model)
        assertEquals("armeabi", obj.architecture)
        assertEquals(false, obj.jailbroken)
        assertEquals("en-US", obj.locale)
        assertEquals(150982302L, obj.internalStorageTotalCapacity)
        assertEquals("android", obj.operatingSystemType)
        assertEquals("10.2.1", obj.operatingSystemVersion)
        assertEquals(29, obj.operatingSystemVersionCode)
        assertEquals("1080x720", obj.screenResolution)
        assertEquals("GMT+1", obj.timezoneDescription)
        assertEquals(8, obj.cores)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<DeviceInfo>()
        assertNotNull(obj)
    }
}
