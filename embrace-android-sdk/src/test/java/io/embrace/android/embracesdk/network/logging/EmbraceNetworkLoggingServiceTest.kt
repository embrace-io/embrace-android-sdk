package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.DomainLocalConfig
import io.embrace.android.embracesdk.config.local.NetworkLocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.NetworkRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureService
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.fakeNetworkBehavior
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.NetworkSessionV2.DomainCount
import io.embrace.android.embracesdk.utils.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.util.UUID

internal class EmbraceNetworkLoggingServiceTest {
    private lateinit var networkLoggingService: EmbraceNetworkLoggingService
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var configService: ConfigService
    private lateinit var sdkLocalConfig: SdkLocalConfig
    private lateinit var remoteConfig: RemoteConfig
    private lateinit var networkCaptureService: FakeNetworkCaptureService
    private lateinit var spanService: FakeSpanService

    @Before
    fun setUp() {
        networkCaptureService = FakeNetworkCaptureService()
        spanService = FakeSpanService()
        logger = InternalEmbraceLogger()
        configService = FakeConfigService(
            networkBehavior = fakeNetworkBehavior(
                localCfg = { sdkLocalConfig },
                remoteCfg = { remoteConfig }
            )
        )

        sdkLocalConfig = SdkLocalConfig()
        remoteConfig = RemoteConfig()
//        createNetworkLoggingService()
    }

//    @Test
//    fun `test getNetworkCallsForSession returns all network calls current stored`() {
//        logNetworkCall("www.example1.com", 100, 200)
//        logNetworkCall("www.example2.com", 200, 300)
//        logNetworkCall("www.example3.com", 300, 400)
//        logNetworkCall("www.example4.com", 400, 500)
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(4, result.requests.size)
//
//        val sortedRequests = result.requests.sortedBy { it.startTime }
//        assertEquals("www.example1.com", sortedRequests.at(0)?.url)
//        assertEquals("www.example2.com", sortedRequests.at(1)?.url)
//        assertEquals("www.example3.com", sortedRequests.at(2)?.url)
//        assertEquals("www.example4.com", sortedRequests.at(3)?.url)
//    }
//
//    @Test
//    fun `test implicit default network call limits`() {
//        repeat(1005) {
//            logNetworkCall("www.overLimit1.com")
//        }
//        logNetworkCall("www.overLimit2.com")
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//
//        assertEquals(1001, result.requests.size)
//        assertEquals(DomainCount(1005, 1000), result.requestCounts["overLimit1.com"])
//        assertNull(result.requestCounts["overLimit2.com"])
//    }
//
//    @Test
//    fun `test domain specific local limits`() {
//        sdkLocalConfig = SdkLocalConfig(
//            networking = NetworkLocalConfig(
//                domains = listOf(DomainLocalConfig("overLimit1.com", 2))
//            )
//        )
//
//        createNetworkLoggingService()
//
//        repeat(3) {
//            logNetworkCall("www.overLimit1.com")
//        }
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(2, result.requests.size)
//        assertEquals(DomainCount(3, 2), result.requestCounts["overLimit1.com"])
//    }
//
//    @Test
//    fun `test default local limits`() {
//        sdkLocalConfig = SdkLocalConfig(
//            networking = NetworkLocalConfig(
//                defaultCaptureLimit = 2
//            )
//        )
//
//        createNetworkLoggingService()
//
//        repeat(4) {
//            logNetworkCall("www.overLimit1.com")
//        }
//
//        logNetworkCall("www.overLimit2.com")
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(3, result.requests.size)
//        assertEquals(DomainCount(4, 2), result.requestCounts["overLimit1.com"])
//        assertNull(result.requestCounts["overLimit2.com"])
//    }
//
//    @Test
//    fun `test local limits with default and domain specific limits`() {
//        sdkLocalConfig = SdkLocalConfig(
//            networking = NetworkLocalConfig(
//                defaultCaptureLimit = 2,
//                domains = listOf(DomainLocalConfig("overLimit1.com", 3))
//            )
//        )
//
//        createNetworkLoggingService()
//
//        repeat(4) {
//            logNetworkCall("www.overLimit1.com")
//        }
//
//        repeat(3) {
//            logNetworkCall("www.overLimit2.com")
//        }
//
//        repeat(2) {
//            logNetworkCall("www.overLimit3.com")
//        }
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(7, result.requests.size)
//        assertEquals(DomainCount(4, 3), result.requestCounts["overLimit1.com"])
//        assertEquals(DomainCount(3, 2), result.requestCounts["overLimit2.com"])
//        assertNull(result.requestCounts["overLimit3.com"])
//    }
//
//    @Test
//    fun `test explicit remote limits as a ceiling for local limit`() {
//        sdkLocalConfig = SdkLocalConfig(
//            networking = NetworkLocalConfig(
//                domains = listOf(DomainLocalConfig("limited.org", 30)),
//                defaultCaptureLimit = 20
//            )
//        )
//
//        remoteConfig = RemoteConfig(
//            networkConfig = NetworkRemoteConfig(
//                domainLimits = mapOf("limited.org" to 10),
//                defaultCaptureLimit = 5
//            )
//        )
//
//        createNetworkLoggingService()
//
//        repeat(30) {
//            logNetworkCall("www.limited.org")
//            logNetworkCall("www.verylimited.com")
//        }
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(15, result.requests.size)
//        assertEquals(DomainCount(30, 10), result.requestCounts["limited.org"])
//        assertEquals(DomainCount(30, 5), result.requestCounts["verylimited.com"])
//    }
//
//    @Test
//    fun `test explicit remote default limit as a ceiling for local limit`() {
//        sdkLocalConfig = SdkLocalConfig(
//            networking = NetworkLocalConfig(
//                domains = listOf(DomainLocalConfig("limited.org", 30)),
//                defaultCaptureLimit = 20
//            )
//        )
//
//        remoteConfig = RemoteConfig(
//            networkConfig = NetworkRemoteConfig(
//                defaultCaptureLimit = 5
//            )
//        )
//
//        createNetworkLoggingService()
//
//        repeat(30) {
//            logNetworkCall("www.limited.org")
//            logNetworkCall("www.verylimited.com")
//        }
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(10, result.requests.size)
//        assertEquals(DomainCount(30, 5), result.requestCounts["limited.org"])
//        assertEquals(DomainCount(30, 5), result.requestCounts["verylimited.com"])
//    }
//
//    @Test
//    fun `test remote domain limit as a ceiling for local limit`() {
//        sdkLocalConfig = SdkLocalConfig(
//            networking = NetworkLocalConfig(
//                domains = listOf(DomainLocalConfig("limited.org", 25)),
//                defaultCaptureLimit = 15
//            )
//        )
//
//        remoteConfig = RemoteConfig(
//            networkConfig = NetworkRemoteConfig(
//                domainLimits = mapOf("limited.org" to 10)
//            )
//        )
//
//        createNetworkLoggingService()
//
//        repeat(30) {
//            logNetworkCall("www.limited.org")
//            logNetworkCall("www.defaultlimit.com")
//        }
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(25, result.requests.size)
//        assertEquals(DomainCount(30, 10), result.requestCounts["limited.org"])
//        assertEquals(DomainCount(30, 15), result.requestCounts["defaultlimit.com"])
//    }
//
//    @Test
//    fun `test implicit remote limit as a ceiling for local limit`() {
//        sdkLocalConfig = SdkLocalConfig(
//            networking = NetworkLocalConfig(
//                domains = listOf(DomainLocalConfig("limited.org", 2000)),
//                defaultCaptureLimit = 1500
//            )
//        )
//
//        remoteConfig = RemoteConfig()
//
//        repeat(2001) {
//            logNetworkCall("www.limited.org")
//            logNetworkCall("www.verylimited.com")
//        }
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(2000, result.requests.size)
//        assertEquals(DomainCount(2001, 1000), result.requestCounts["limited.org"])
//        assertEquals(DomainCount(2001, 1000), result.requestCounts["verylimited.com"])
//    }
//
//    @Test
//    fun `limit applies to all domains with a given suffix`() {
//        remoteConfig = RemoteConfig(
//            networkConfig = NetworkRemoteConfig(
//                domainLimits = mapOf("limited.org" to 15),
//                defaultCaptureLimit = 10
//            )
//        )
//
//        createNetworkLoggingService()
//
//        repeat(8) {
//            logNetworkCall("www.limited.org")
//            logNetworkCall("admin.limited.org")
//            logNetworkCall("verylimited.org")
//            logNetworkCall("woopwoop.com")
//        }
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(23, result.requests.size)
//        assertEquals(1, result.requestCounts.size)
//        assertEquals(DomainCount(24, 15), result.requestCounts["limited.org"])
//    }
//
//    @Test
//    fun `fetching session doesn't reset limit`() {
//        remoteConfig = RemoteConfig(
//            networkConfig = NetworkRemoteConfig(
//                defaultCaptureLimit = 5
//            )
//        )
//
//        createNetworkLoggingService()
//
//        repeat(6) {
//            logNetworkCall("www.limited.org")
//        }
//
//        networkLoggingService.getNetworkCallsSnapshot()
//
//        repeat(6) {
//            logNetworkCall("www.limited.org")
//        }
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(5, result.requests.size)
//        assertEquals(DomainCount(12, 5), result.requestCounts["limited.org"])
//    }
//
//    @Test
//    fun `clearing service resets the limit`() {
//        remoteConfig = RemoteConfig(
//            networkConfig = NetworkRemoteConfig(
//                defaultCaptureLimit = 5
//            )
//        )
//
//        createNetworkLoggingService()
//
//        repeat(10) {
//            logNetworkCall("www.limited.org")
//        }
//
//        val firstSession = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(5, firstSession.requests.size)
//        assertEquals(DomainCount(10, 5), firstSession.requestCounts["limited.org"])
//
//        networkLoggingService.cleanCollections()
//
//        repeat(6) {
//            logNetworkCall("www.limited.org")
//        }
//
//        val secondSession = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(5, secondSession.requests.size)
//        assertEquals(DomainCount(6, 5), secondSession.requestCounts["limited.org"])
//    }
//
//    @Test
//    fun logNetworkErrorTest() {
//        val url = "192.168.0.40:8080/test"
//        val httpMethod = "GET"
//        val startTime = 10000L
//        val endTime = 20000L
//
//        networkLoggingService.logNetworkError(
//            randomId(),
//            url,
//            httpMethod,
//            startTime,
//            endTime,
//            "test",
//            "test",
//            null,
//            null,
//            null
//        )
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//
//        assertEquals(url, result.requests.at(0)?.url)
//    }
//
//    @Test
//    fun `test logNetworkCall sends the network body if necessary`() {
//        val url = "www.example.com"
//        networkLoggingService.logNetworkCall(
//            randomId(),
//            url,
//            "GET",
//            200,
//            10000L,
//            20000L,
//            1000L,
//            1000L,
//            null,
//            null,
//            NetworkCaptureData(
//                null,
//                null,
//                null,
//                null,
//                null,
//                null
//            )
//        )
//        assertEquals(url, networkCaptureService.urls.single())
//    }
//
//    @Test
//    fun `test logNetworkCall doesn't send the network body if null`() {
//        networkLoggingService.logNetworkCall(
//            randomId(),
//            "www.example.com",
//            "GET",
//            200,
//            10000L,
//            20000L,
//            1000L,
//            1000L,
//            null,
//            null,
//            null
//        )
//        assertTrue(networkCaptureService.urls.isEmpty())
//    }
//
//    @Test
//    fun cleanCollections() {
//        logNetworkCall("192.168.0.40:8080/test")
//        logNetworkCall("192.168.0.40:8080/test")
//        logNetworkCall("www.example.com")
//        logNetworkCall("www.example.com")
//
//        networkLoggingService.cleanCollections()
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//
//        assertEquals(0, result.requests.size)
//        assertEquals(0, result.requestCounts.size)
//    }
//
//    @Test
//    fun `network requests with the same start time will be recorded each time`() {
//        val startTime = 99L
//        val endTime = 300L
//        repeat(2) {
//            logNetworkCall(url = "https://embrace.io", startTime = startTime, endTime = endTime)
//        }
//
//        repeat(2) {
//            logNetworkError(url = "https://embrace.io", startTime = startTime)
//        }
//
//        assertEquals(4, networkLoggingService.getNetworkCallsSnapshot().requests.size)
//    }
//
//    @Test
//    fun `network requests with the same callId will be logged once with last writer wins`() {
//        val callId = UUID.randomUUID().toString()
//        val expectedStartTime = 99L
//        val expectedEndTime = 300L
//        val expectedUrl = "https://embrace.io/forreal"
//
//        logNetworkCall(url = "https://embrace.io", startTime = 50, endTime = 100, callId = callId)
//        logNetworkError(url = "https://embrace.io", startTime = 50, callId = callId)
//        logNetworkCall(url = expectedUrl, startTime = expectedStartTime, endTime = expectedEndTime, callId = callId)
//
//        val result = networkLoggingService.getNetworkCallsSnapshot()
//        assertEquals(1, result.requests.size)
//        with(result.requests[0]) {
//            assertEquals(1, result.requests.size)
//            assertEquals(expectedStartTime, startTime)
//            assertEquals(expectedEndTime, endTime)
//            assertEquals(expectedUrl, url)
//        }
//    }
//
//    private fun createNetworkLoggingService() {
//        networkLoggingService =
//            EmbraceNetworkLoggingService(
//                configService,
//                logger,
//                networkCaptureService,
//                spanService
//            )
//    }
//
//    private fun logNetworkCall(url: String, startTime: Long = 100, endTime: Long = 200, callId: String = randomId()) {
//        networkLoggingService.logNetworkCall(
//            callId,
//            url,
//            "GET",
//            200,
//            startTime,
//            endTime,
//            1000L,
//            1000L,
//            null,
//            null,
//            null
//        )
//    }
//
//    private fun logNetworkError(url: String, startTime: Long = 100, callId: String = randomId()) {
//        networkLoggingService.logNetworkError(
//            callId,
//            url,
//            "GET",
//            startTime,
//            0,
//            NullPointerException::class.java.canonicalName,
//            "NPE baby",
//            null,
//            null,
//            null
//        )
//    }

    private fun randomId(): String = UUID.randomUUID().toString()
}
