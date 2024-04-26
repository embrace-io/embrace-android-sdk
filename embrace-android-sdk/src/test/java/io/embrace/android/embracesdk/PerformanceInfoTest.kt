package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.payload.NetworkRequests
import io.embrace.android.embracesdk.payload.NetworkSessionV2
import io.embrace.android.embracesdk.payload.PerformanceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PerformanceInfoTest {

    private val diskUsage: DiskUsage = DiskUsage(10000000, 2000000)
    private val networkRequests: NetworkRequests = NetworkRequests(NetworkSessionV2(emptyList(), emptyMap()))
    private val googleAnrTimestamps: List<Long> = emptyList()
    private val appExitInfoData: List<AppExitInfoData> = emptyList()

    @Test
    fun testPerfInfoSerialization() {
        assertJsonMatchesGoldenFile("perf_info_expected.json", buildPerformanceInfo())
    }

    @Test
    fun testPerfInfoDeserialization() {
        val obj = deserializeJsonFromResource<PerformanceInfo>("perf_info_expected.json")
        verifyFields(obj)
    }

    @Test
    fun testPerfInfoEmptyObject() {
        val obj = deserializeEmptyJsonString<PerformanceInfo>()
        assertNotNull(obj)
    }

    private fun verifyFields(performanceInfo: PerformanceInfo) {
        assertEquals(googleAnrTimestamps, performanceInfo.googleAnrTimestamps)
    }

    private fun buildPerformanceInfo(): PerformanceInfo = PerformanceInfo(
        appExitInfoData = appExitInfoData,
        diskUsage = diskUsage,
        googleAnrTimestamps = googleAnrTimestamps,
        networkRequests = networkRequests
    )
}
