package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.anr.detection.ResponsivenessMonitor
import io.embrace.android.embracesdk.payload.AnrInterval
import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.payload.Interval
import io.embrace.android.embracesdk.payload.MemoryWarning
import io.embrace.android.embracesdk.payload.NativeThreadAnrInterval
import io.embrace.android.embracesdk.payload.NetworkRequests
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.PowerModeInterval
import io.embrace.android.embracesdk.payload.StrictModeViolation
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PerformanceInfoTest {

    private val diskUsage: DiskUsage = DiskUsage(10000000, 2000000)
    private val networkRequests: NetworkRequests = mockk()
    private val memoryWarnings: List<MemoryWarning> = emptyList()
    private val networkInterfaceIntervals: List<Interval> = emptyList()
    private val googleAnrTimestamps: List<Long> = emptyList()
    private val anrIntervals: List<AnrInterval> = emptyList()
    private val appExitInfoData: List<AppExitInfoData> = mockk(relaxed = true)
    private val nativeThreadAnrIntervals: List<NativeThreadAnrInterval> = emptyList()
    private val powerSaveModeIntervals: List<PowerModeInterval> = emptyList()
    private val violations: List<StrictModeViolation> = emptyList()
    private val threadMonitorSnapshots: List<ResponsivenessMonitor.Snapshot> = emptyList()

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
        assertEquals(anrIntervals, performanceInfo.anrIntervals)
        assertEquals(googleAnrTimestamps, performanceInfo.googleAnrTimestamps)
        assertEquals(memoryWarnings, performanceInfo.memoryWarnings)
        assertEquals(nativeThreadAnrIntervals, performanceInfo.nativeThreadAnrIntervals)
        assertEquals(networkInterfaceIntervals, performanceInfo.networkInterfaceIntervals)
        assertEquals(powerSaveModeIntervals, performanceInfo.powerSaveModeIntervals)
        assertEquals(violations, performanceInfo.strictmodeViolations)
        assertEquals(threadMonitorSnapshots, performanceInfo.responsivenessMonitorSnapshots)
    }

    private fun buildPerformanceInfo(): PerformanceInfo = PerformanceInfo(
        anrIntervals = anrIntervals,
        appExitInfoData = appExitInfoData,
        diskUsage = diskUsage,
        googleAnrTimestamps = googleAnrTimestamps,
        memoryWarnings = memoryWarnings,
        nativeThreadAnrIntervals = nativeThreadAnrIntervals,
        networkInterfaceIntervals = networkInterfaceIntervals,
        powerSaveModeIntervals = powerSaveModeIntervals,
        networkRequests = networkRequests,
        strictmodeViolations = violations,
        responsivenessMonitorSnapshots = threadMonitorSnapshots
    )
}
