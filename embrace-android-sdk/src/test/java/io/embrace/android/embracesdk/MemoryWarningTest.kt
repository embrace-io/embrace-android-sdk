package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.payload.MemoryWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class MemoryWarningTest {

    private val serializer = EmbraceSerializer()
    private val info = MemoryWarning(16098234098234)

    @Test
    fun testSerialization() {
        val expectedInfo = ResourceReader.readResourceAsText("memory_warning_expected.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("memory_warning_expected.json")
        val obj = serializer.fromJson(json, MemoryWarning::class.java)
        assertEquals(16098234098234, obj.timestamp)
    }

    @Test
    fun testEmptyObject() {
        val info = serializer.fromJson("{}", MemoryWarning::class.java)
        assertNotNull(info)
    }
}
