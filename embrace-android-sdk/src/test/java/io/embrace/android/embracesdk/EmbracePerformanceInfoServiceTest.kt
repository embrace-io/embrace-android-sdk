package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.anr.sigquit.GoogleAnrTimestampRepository
import io.embrace.android.embracesdk.capture.EmbracePerformanceInfoService
import io.embrace.android.embracesdk.capture.monitor.NoOpResponsivenessMonitorService
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeApplicationExitInfoService
import io.embrace.android.embracesdk.fakes.FakeMemoryService
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeNetworkLoggingService
import io.embrace.android.embracesdk.fakes.FakePowerSaveModeService
import io.embrace.android.embracesdk.fakes.FakeStrictModeService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.NetworkRequests
import io.embrace.android.embracesdk.payload.PerformanceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val SESSION_END_TIME_MS = 1609234092345

internal class EmbracePerformanceInfoServiceTest {

    private lateinit var service: EmbracePerformanceInfoService
    private val anrService = FakeAnrService()
    private val networkConnectivityService = FakeNetworkConnectivityService()
    private val networkLoggingService = FakeNetworkLoggingService()
    private val powerSaveModeService = FakePowerSaveModeService()
    private val memoryService = FakeMemoryService()
    private val metadataService = FakeAndroidMetadataService()
    private val googleAnrTimestampRepository = GoogleAnrTimestampRepository(InternalEmbraceLogger())
    private val applicationExitInfoService = FakeApplicationExitInfoService()
    private val strictModeService = FakeStrictModeService()
    private val monitoringServiceRule = NoOpResponsivenessMonitorService()

    @Before
    fun setUp() {
        service = EmbracePerformanceInfoService(
            anrService,
            networkConnectivityService,
            networkLoggingService,
            powerSaveModeService,
            memoryService,
            metadataService,
            googleAnrTimestampRepository,
            applicationExitInfoService,
            strictModeService,
            null,
            monitoringServiceRule
        )
        googleAnrTimestampRepository.add(150209234099)
    }

    @Test
    fun testPerformanceInfo() {
        val info = service.getPerformanceInfo(0, SESSION_END_TIME_MS, false)
        assertBasicPerfInfoIncluded(info)
    }

    @Test
    fun testSessionPerformanceInfoNonColdStart() {
        val info = service.getSessionPerformanceInfo(0, SESSION_END_TIME_MS, false, null)
        assertBasicPerfInfoIncluded(info)
        assertBasicSessionPerfInfoIncluded(info)

        // verify certain fields were not included in non-cold start
        assertNull(info.appExitInfoData)
    }

    @Test
    fun testSessionPerformanceInfoColdStart() {
        val info = service.getSessionPerformanceInfo(0, SESSION_END_TIME_MS, true, null)
        assertBasicPerfInfoIncluded(info)
        assertBasicSessionPerfInfoIncluded(info)

        assertValueCopied(anrService.data, info.anrIntervals)
        assertValueCopied(applicationExitInfoService.data, info.appExitInfoData)
    }

    private fun assertBasicPerfInfoIncluded(info: PerformanceInfo) {
        assertValueCopied(metadataService.getDiskUsage(), info.diskUsage)
        assertValueCopied(memoryService.data, info.memoryWarnings)
        assertValueCopied(networkConnectivityService.data, info.networkInterfaceIntervals)
        assertValueCopied(powerSaveModeService.data, info.powerSaveModeIntervals)
    }

    private fun assertBasicSessionPerfInfoIncluded(info: PerformanceInfo) {
        assertValueCopied(NetworkRequests(networkLoggingService.data), info.networkRequests)
        assertValueCopied(anrService.data, info.anrIntervals)
        assertValueCopied(
            googleAnrTimestampRepository.getGoogleAnrTimestamps(0, SESSION_END_TIME_MS),
            info.googleAnrTimestamps
        )
        assertValueCopied(strictModeService.data, info.strictmodeViolations)
    }

    private fun assertValueCopied(expected: Any?, observed: Any?) {
        checkNotNull(expected)
        checkNotNull(observed)
        assertEquals(expected, observed)
        assertNotSame(
            "An original reference was added to the PerformanceInfo object. This can lead to " +
                "data corruption & thread safety issues. Please fix EmbracePerformanceInfoService so that " +
                "it makes a proper defensive copy for this property.",
            expected,
            observed
        )
    }
}
