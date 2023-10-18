package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.DomainLocalConfig
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.NetworkLocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.fakeNetworkBehavior
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.NetworkSessionV2.DomainCount
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.embrace.android.embracesdk.utils.at
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class EmbraceNetworkLoggingServiceTest {
    private lateinit var service: EmbraceNetworkLoggingService

    companion object {
        private lateinit var configService: ConfigService
        private lateinit var localConfig: LocalConfig
        private lateinit var memoryCleanerService: MemoryCleanerService
        private lateinit var metadataService: FakeAndroidMetadataService
        private lateinit var logger: InternalEmbraceLogger
        private lateinit var networkCaptureService: EmbraceNetworkCaptureService
        private lateinit var cfg: SdkLocalConfig

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            configService = mockk(relaxed = true) {
                every { networkBehavior } returns fakeNetworkBehavior(
                    localCfg = { cfg }
                )
            }
            localConfig = mockk(relaxed = true)
            memoryCleanerService = mockk(relaxed = true)
            logger = InternalEmbraceLogger()
            metadataService = FakeAndroidMetadataService()
            networkCaptureService = mockk(relaxed = true)
        }

        @AfterClass
        fun tearDown() {
            unmockkAll()
        }
    }

    @Before
    fun setUp() {
        cfg = SdkLocalConfig(networking = NetworkLocalConfig())

        clearAllMocks(
            answers = false,
            objectMocks = false,
            constructorMocks = false,
            staticMocks = false
        )

        service =
            EmbraceNetworkLoggingService(
                configService,
                logger,
                networkCaptureService
            )
    }

    @Test
    fun `test getNetworkCallsForSession returns all network calls current stored`() {
        logNetworkCall("www.example1.com", 100, 200)
        logNetworkCall("www.example2.com", 200, 300)
        logNetworkCall("www.example3.com", 300, 400)
        logNetworkCall("www.example4.com", 400, 500)

        val result = service.getNetworkCallsForSession()

        // test use only session calls
        assertEquals(4, result.requests.size)
        assertEquals("www.example1.com", result.requests.at(0)?.url)
        assertEquals("www.example2.com", result.requests.at(1)?.url)
        assertEquals("www.example3.com", result.requests.at(2)?.url)
        assertEquals("www.example4.com", result.requests.at(3)?.url)
    }

    @Test
    fun `test getNetworkCallsForSession over limit`() {
        every { configService.networkBehavior.getNetworkCaptureLimit() }.returns(
            2
        )

        logNetworkCall("www.overLimit1.com")
        logNetworkCall("www.overLimit1.com")
        logNetworkCall("www.overLimit1.com")
        logNetworkCall("www.overLimit1.com")
        logNetworkCall("www.overLimit2.com")
        logNetworkCall("www.overLimit2.com")
        logNetworkCall("www.overLimit3.com")

        val result = service.getNetworkCallsForSession()

        // overLimit1 has 4 calls. The limit is 2.
        val expectedOverLimit = DomainCount(4, 2)
        assertEquals(1, result.requestCounts.size)

        assertEquals(expectedOverLimit, result.requestCounts["overLimit1.com"])
        assertNull(result.requestCounts["overLimit2.com"])
        assertNull(result.requestCounts["overLimit3.com"])
    }

    @Test
    fun `test getNetworkCallsForSession merged limits`() {
        cfg = SdkLocalConfig(
            networking = NetworkLocalConfig(
                domains = listOf(DomainLocalConfig("overLimit1.com", 2))
            )
        )

        logNetworkCall("www.overLimit1.com")
        logNetworkCall("www.overLimit1.com")
        logNetworkCall("www.overLimit1.com")
        logNetworkCall("www.overLimit1.com")
        logNetworkCall("www.overLimit2.com")
        logNetworkCall("www.overLimit2.com")
        logNetworkCall("www.overLimit3.com")

        val result = service.getNetworkCallsForSession()

        // overLimit1 has 4 calls. The local limit is 2.
        val expectedOverLimit = DomainCount(4, 2)
        assertEquals(1, result.requestCounts.size)

        assertEquals(expectedOverLimit, result.requestCounts["overLimit1.com"])
        assertNull(result.requestCounts["overLimit2.com"])
        assertNull(result.requestCounts["overLimit3.com"])
    }

    @Test
    fun logNetworkErrorTest() {
        val url = "192.168.0.40:8080/test"
        val httpMethod = "GET"
        val startTime = 10000L
        val endTime = 20000L

        service.logNetworkError(
            url,
            httpMethod,
            startTime,
            endTime,
            "test",
            "test",
            null,
            null,
            null
        )

        val result = service.getNetworkCallsForSession()

        assertEquals(url, result.requests.at(0)?.url)
    }

    @Test
    fun `test logNetworkCall sends the network body if necessary`() {
        service.logNetworkCall(
            "www.example.com",
            "GET",
            200,
            10000L,
            20000L,
            1000L,
            1000L,
            null,
            null,
            mockk(relaxed = true)
        )

        verify(exactly = 1) {
            networkCaptureService.logNetworkCapturedData(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `test logNetworkCall doesn't send the network body if null`() {
        service.logNetworkCall(
            "www.example.com",
            "GET",
            200,
            10000L,
            20000L,
            1000L,
            1000L,
            null,
            null,
            null
        )

        verify(exactly = 0) {
            networkCaptureService.logNetworkCapturedData(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun cleanCollections() {
        logNetworkCall("192.168.0.40:8080/test")
        logNetworkCall("192.168.0.40:8080/test")
        logNetworkCall("www.example.com")
        logNetworkCall("www.example.com")

        service.cleanCollections()

        val result = service.getNetworkCallsForSession()

        assertEquals(0, result.requests.size)
        assertEquals(0, result.requestCounts.size)
    }

    private fun logNetworkCall(url: String, startTime: Long = 100, endTime: Long = 200) {
        service.logNetworkCall(
            url,
            "GET",
            200,
            startTime,
            endTime,
            1000L,
            1000L,
            null,
            null,
            null
        )
    }
}
