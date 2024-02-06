package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.DiskUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DiskUsageTest {

    private val info = DiskUsage(
        150982302,
        150923,
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("disk_usage_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<DiskUsage>("disk_usage_expected.json")
        assertEquals(150982302L, obj.appDiskUsage)
        assertEquals(150923L, obj.deviceDiskFree)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<DiskUsage>()
        assertNotNull(obj)
    }
}
