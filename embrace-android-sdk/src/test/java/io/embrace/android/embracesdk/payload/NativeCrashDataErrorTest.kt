package io.embrace.android.embracesdk.payload

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
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
        val expectedInfo = ResourceReader.readResourceAsText("native_crash_data_error_expected.json")
            .filter { !it.isWhitespace() }
        val observed = Gson().toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("native_crash_data_error_expected.json")
        val obj = Gson().fromJson(json, NativeCrashDataError::class.java)
        assertEquals(5, obj.number)
        assertEquals(2, obj.context)
    }

    @Test
    fun testEmptyObject() {
        val info = Gson().fromJson("{}", NativeCrashDataError::class.java)
        assertNotNull(info)
    }
}
