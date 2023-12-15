package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.LocalConfigParser
import io.embrace.android.embracesdk.config.local.BaseUrlLocalConfig
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.event.EmbraceRemoteLogger
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.fakeNetworkBehavior
import io.embrace.android.embracesdk.fakes.fakeSdkEndpointBehavior
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.prefs.EmbracePreferencesService
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class EmbraceNetworkCaptureServiceTest {

    companion object {
        private val metadataService: FakeAndroidMetadataService = FakeAndroidMetadataService()
        private val mockRemoteLogger: EmbraceRemoteLogger = mockk(relaxed = true)
        private val configService: ConfigService = mockk(relaxed = true)
        private val mockPreferenceService: EmbracePreferencesService = mockk(relaxed = true)
        private lateinit var mockLocalConfig: LocalConfig

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockLocalConfig =
                LocalConfigParser.buildConfig(
                    "GrCPU",
                    false,
                    "{\"base_urls\": {\"data\": \"https://data.emb-api.com\"}}",
                    EmbraceSerializer()
                )
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    private var cfg: RemoteConfig = RemoteConfig()

    @Before
    fun setUp() {
        clearAllMocks()
        metadataService.setActiveSessionId("session-123", true)
        every { configService.networkBehavior } returns fakeNetworkBehavior { cfg }
        every { configService.sdkEndpointBehavior } returns fakeSdkEndpointBehavior { BaseUrlLocalConfig() }
    }

    @Test
    fun testUrlMatch() {
        val regex = "httpbin.org/*".toRegex()
        val url = "https://httpbin.org/get"
        assertTrue(regex.containsMatchIn(url))
    }

    @Test
    fun `test no capture rules`() {
        val result = getService().getNetworkCaptureRules("url", "GET")
        assertEquals(0, result.size)
    }

    @Test
    fun `test no capture for URL`() {
        val rule = getDefaultRule(urlRegex = "embrace.io/*")
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        val result = getService().getNetworkCaptureRules("url", "GET")
        assertEquals(0, result.size)
    }

    @Test
    fun `test capture rule doesn't capture Embrace endpoints`() {
        val rule = getDefaultRule(urlRegex = "https://a-o0o0o.data.emb-api.com")
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        val result = getService().getNetworkCaptureRules("https://a-o0o0o.data.emb-api.com", "GET")
        assertEquals(0, result.size)
    }

    @Test
    fun `test capture rule expires in`() {
        val rule = getDefaultRule(expiresIn = 0)
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        val result = getService().getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertEquals(0, result.size)
    }

    @Test
    fun `test capture rule maxCount discount 1`() {
        val rule = getDefaultRule()
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        every { mockPreferenceService.isNetworkCaptureRuleOver(any()) } returns false
        val result = getService().getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertEquals(1, result.size)
        every { mockPreferenceService.isNetworkCaptureRuleOver(any()) } returns true
        val emptyRule = getService().getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertEquals(0, emptyRule.size)
    }

    @Test
    fun `test capture rule maxCount is over`() {
        val rule = getDefaultRule()
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        every { mockPreferenceService.isNetworkCaptureRuleOver(any()) } returns true
        val emptyRule = getService().getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertEquals(0, emptyRule.size)
    }

    @Test
    fun `test capture rule matches URL and method `() {
        val rule = getDefaultRule(
            method = "GET, POST",
            urlRegex = "embrace.io/*"
        )
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        val result = getService().getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `test capture rule doesnt match URL and method `() {
        val rule = getDefaultRule(
            method = "POST",
            urlRegex = "embrace.io/*"
        )
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        val result = getService().getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test capture rule duration`() {
        // capture calls that exceeds 5000ms
        val rule = getDefaultRule(duration = 5000)
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))

        val service = getService()
        // duration = 2000ms shouldn't be captured
        service.logNetworkCapturedData(
            "https://embrace.io/changelog",
            "GET",
            200,
            0,
            2000,
            mockk(relaxed = true)
        )
        verify(exactly = 0) { mockRemoteLogger.logNetwork(any()) }

        // duration = 6000ms should be captured
        service.logNetworkCapturedData(
            "https://embrace.io/changelog",
            "GET",
            200,
            0,
            6000,
            mockk(relaxed = true)
        )
        verify(exactly = 1) { mockRemoteLogger.logNetwork(any()) }
    }

    @Test
    fun `test capture rule status codes `() {
        val rule = getDefaultRule(statusCodes = setOf(200, 404))
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))

        val service = getService()
        service.logNetworkCapturedData(
            "https://embrace.io/changelog",
            "GET",
            200,
            0,
            2000,
            mockk(relaxed = true)
        )
        verify(exactly = 1) { mockRemoteLogger.logNetwork(any()) }

        service.logNetworkCapturedData(
            "https://embrace.io/changelog",
            "GET",
            404,
            0,
            2000,
            mockk(relaxed = true)
        )
        verify(exactly = 2) { mockRemoteLogger.logNetwork(any()) }

        service.logNetworkCapturedData(
            "https://embrace.io/changelog",
            "GET",
            500,
            0,
            2000,
            mockk(relaxed = true)
        )
        verify(exactly = 2) { mockRemoteLogger.logNetwork(any()) }
    }

    private fun getService() = EmbraceNetworkCaptureService(
        metadataService,
        mockPreferenceService,
        mockRemoteLogger,
        configService,
        EmbraceSerializer()
    )

    private fun getDefaultRule(
        id: String = "123",
        duration: Long = 0,
        expiresIn: Long = 123,
        method: String = "GET, POST",
        maxSize: Long = 102400L,
        maxCount: Int = 5,
        statusCodes: Set<Int> = setOf(200, 404),
        urlRegex: String = "embrace.io/*"
    ) =
        NetworkCaptureRuleRemoteConfig(
            id = id,
            duration = duration,
            method = method,
            urlRegex = urlRegex,
            expiresIn = expiresIn,
            maxSize = maxSize,
            maxCount = maxCount,
            statusCodes = statusCodes
        )
}
