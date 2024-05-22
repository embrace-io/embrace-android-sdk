package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.payload.PerformanceInfo
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PerformanceInfoTest {

    private val diskUsage: DiskUsage = DiskUsage(10000000, 2000000)

    @Test
    fun testPerfInfoSerialization() {
        assertJsonMatchesGoldenFile("perf_info_expected.json", buildPerformanceInfo())
    }

    @Test
    fun testPerfInfoDeserialization() {
        val obj = deserializeJsonFromResource<PerformanceInfo>("perf_info_expected.json")
        assertNotNull(obj)
    }

    @Test
    fun testPerfInfoEmptyObject() {
        val obj = deserializeEmptyJsonString<PerformanceInfo>()
        assertNotNull(obj)
    }

    private fun buildPerformanceInfo(): PerformanceInfo = PerformanceInfo(diskUsage = diskUsage)
}
