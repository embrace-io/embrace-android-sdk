package io.embrace.android.embracesdk.payload

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
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
        val expectedInfo = ResourceReader.readResourceAsText("native_crash_metadata_expected.json")
            .filter { !it.isWhitespace() }
        val observed = Gson().toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("native_crash_metadata_expected.json")
        val obj = Gson().fromJson(json, NativeCrashMetadata::class.java)
        verifyInfoPopulated(obj)
    }

    @Test
    fun testToJsonImpl() {
        val json = info.toJson()
        val obj = Gson().fromJson(json, NativeCrashMetadata::class.java)
        verifyInfoPopulated(obj)
    }

    @Test
    fun testEmptyObject() {
        val info = Gson().fromJson("{}", NativeCrashMetadata::class.java)
        assertNotNull(info)
    }

    private fun verifyInfoPopulated(obj: NativeCrashMetadata) {
        assertEquals("1.0", obj.appInfo.appVersion)
        assertEquals("samsung", obj.deviceInfo.manufacturer)
        assertEquals("123", obj.userInfo.userId)
        assertEquals("value", checkNotNull(obj.sessionProperties)["key"])
    }
}
