package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class NativeCrashDataTest {

    private val serializer = EmbraceSerializer()

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
        val expectedInfo = ResourceReader.readResourceAsText("native_crash_data_expected.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("native_crash_data_expected.json")
        val obj = serializer.fromJson(json, NativeCrashData::class.java)
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

    @Test
    fun testEmptyObject() {
        val info = serializer.fromJson("{}", NativeCrashData::class.java)
        assertNotNull(info)
    }
}
