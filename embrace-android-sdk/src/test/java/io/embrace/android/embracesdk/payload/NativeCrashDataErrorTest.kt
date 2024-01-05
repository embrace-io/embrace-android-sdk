package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class NativeCrashDataErrorTest {

    private val info =
        NativeCrashDataError(
            5,
            2
        )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("native_crash_data_error_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<NativeCrashDataError>("native_crash_data_error_expected.json")
        assertEquals(5, obj.number)
        assertEquals(2, obj.context)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<NativeCrashDataError>()
        assertNotNull(obj)
    }
}
