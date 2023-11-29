package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.payload.DiskUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DiskUsageTest {

    private val serializer = EmbraceSerializer()

    private val info = DiskUsage(
        150982302,
        150923,
    )

    @Test
    fun testSerialization() {
        val expectedInfo = ResourceReader.readResourceAsText("disk_usage_expected.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("disk_usage_expected.json")
        val obj = serializer.fromJson(json, DiskUsage::class.java)
        assertEquals(150982302L, obj.appDiskUsage)
        assertEquals(150923L, obj.deviceDiskFree)
    }

    @Test
    fun testEmptyObject() {
        val info = serializer.fromJson("{}", DiskUsage::class.java)
        assertNotNull(info)
    }
}
