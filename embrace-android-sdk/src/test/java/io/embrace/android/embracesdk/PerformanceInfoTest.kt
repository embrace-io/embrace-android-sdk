package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.payload.NativeThreadAnrInterval
import io.embrace.android.embracesdk.payload.NetworkRequests
import io.embrace.android.embracesdk.payload.NetworkSessionV2
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.ResponsivenessSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PerformanceInfoTest {

    private val diskUsage: DiskUsage = DiskUsage(10000000, 2000000)
    private val networkRequests: NetworkRequests = NetworkRequests(NetworkSessionV2(emptyList(), emptyMap()))
    private val googleAnrTimestamps: List<Long> = emptyList()
    private val appExitInfoData: List<AppExitInfoData> = emptyList()
    private val nativeThreadAnrIntervals: List<NativeThreadAnrInterval> = emptyList()
    private val threadMonitorSnapshots: List<ResponsivenessSnapshot> = emptyList()

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
        assertEquals(nativeThreadAnrIntervals, performanceInfo.nativeThreadAnrIntervals)
        assertEquals(threadMonitorSnapshots, performanceInfo.responsivenessMonitorSnapshots)
    }

    private fun buildPerformanceInfo(): PerformanceInfo = PerformanceInfo(
        appExitInfoData = appExitInfoData,
        diskUsage = diskUsage,
        googleAnrTimestamps = googleAnrTimestamps,
        nativeThreadAnrIntervals = nativeThreadAnrIntervals,
        networkRequests = networkRequests,
        responsivenessMonitorSnapshots = threadMonitorSnapshots
    )
}
