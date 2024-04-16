package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.anr.sigquit.GoogleAnrTimestampRepository
import io.embrace.android.embracesdk.capture.EmbracePerformanceInfoService
import io.embrace.android.embracesdk.capture.monitor.NoOpResponsivenessMonitorService
import io.embrace.android.embracesdk.fakes.FakeMemoryService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeNetworkLoggingService
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
    private val networkConnectivityService = FakeNetworkConnectivityService()
    private val networkLoggingService = FakeNetworkLoggingService()
    private val metadataService = FakeMetadataService()
    private val googleAnrTimestampRepository = GoogleAnrTimestampRepository(InternalEmbraceLogger())
    private val monitoringServiceRule = NoOpResponsivenessMonitorService()

    @Before
    fun setUp() {
        service = EmbracePerformanceInfoService(
            networkConnectivityService,
            networkLoggingService,
            metadataService,
            googleAnrTimestampRepository,
            null,
            monitoringServiceRule,
            InternalEmbraceLogger()
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
    }

    private fun assertBasicPerfInfoIncluded(info: PerformanceInfo) {
        assertValueCopied(metadataService.getDiskUsage(), info.diskUsage)
        assertValueCopied(networkConnectivityService.data, info.networkInterfaceIntervals)
    }

    private fun assertBasicSessionPerfInfoIncluded(info: PerformanceInfo) {
        assertValueCopied(NetworkRequests(networkLoggingService.data), info.networkRequests)
        assertValueCopied(
            googleAnrTimestampRepository.getGoogleAnrTimestamps(0, SESSION_END_TIME_MS),
            info.googleAnrTimestamps
        )
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
