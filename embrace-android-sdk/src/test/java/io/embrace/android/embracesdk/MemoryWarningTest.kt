package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.MemoryWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class MemoryWarningTest {

    private val info = MemoryWarning(16098234098234)

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("memory_warning_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<MemoryWarning>("memory_warning_expected.json")
        assertEquals(16098234098234, obj.timestamp)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<MemoryWarning>()
        assertNotNull(obj)
    }
}
