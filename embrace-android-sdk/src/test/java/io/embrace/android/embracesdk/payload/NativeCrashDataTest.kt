package io.embrace.android.embracesdk.payload

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class NativeCrashDataTest {

    private val info = NativeCrashData(
        "report_id",
        "sid",
        1610000000000,
        "app_state",
        NativeCrashMetadata(
            AppInfo(),
            DeviceInfo(),
            UserInfo(),
            emptyMap()
        ),
        2,
        "crash",
        mapOf("key" to "value"),
        listOf(
            NativeCrashDataError(
                5,
                2
            )
        ),
        "map"
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("native_crash_data_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<NativeCrashData>("native_crash_data_expected.json")
        assertEquals("report_id", obj.nativeCrashId)
        assertEquals("sid", obj.sessionId)
        assertEquals(1610000000000, obj.timestamp)
        assertEquals("app_state", obj.appState)
        assertNotNull(obj.metadata)
        assertEquals(2, obj.unwindError)
        assertNotNull(obj.getCrash())
        assertEquals(mapOf("key" to "value"), obj.symbols)
        assertEquals(1, obj.errors?.size)
        assertEquals("map", obj.map)
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<NativeCrashData>()
    }
}
