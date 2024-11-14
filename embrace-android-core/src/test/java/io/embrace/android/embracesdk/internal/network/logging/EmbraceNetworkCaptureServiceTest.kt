package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureDataSource
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.createNetworkBehavior
import io.embrace.android.embracesdk.internal.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceNetworkCaptureServiceTest {

    private lateinit var preferenceService: FakePreferenceService
    private lateinit var networkCaptureDataSource: FakeNetworkCaptureDataSource
    private lateinit var cfg: RemoteConfig
    private val sessionIdTracker: FakeSessionIdTracker = FakeSessionIdTracker()
    private lateinit var configService: FakeConfigService
    private val networkCaptureData: NetworkCaptureData = NetworkCaptureData(
        null,
        null,
        null,
        null,
        null,
        null
    )

    @Before
    fun setUp() {
        cfg = RemoteConfig()
        configService = FakeConfigService(
            networkBehavior = createNetworkBehavior(remoteCfg = cfg)
        )
        preferenceService = FakePreferenceService()
        networkCaptureDataSource = FakeNetworkCaptureDataSource()
        sessionIdTracker.setActiveSession("session-123", true)
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
        val rule = getDefaultRule(urlRegex = "https://a-abcde.data.emb-api.com/api/v2")
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        val result = getService().getNetworkCaptureRules("https://a-abcde.data.emb-api.com/api/v2/spans", "GET")
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
        preferenceService.networkCaptureRuleOver = false
        val result = getService().getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertEquals(1, result.size)
        preferenceService.networkCaptureRuleOver = true
        val emptyRule = getService().getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertEquals(0, emptyRule.size)
    }

    @Test
    fun `test capture rule maxCount is over`() {
        val rule = getDefaultRule()
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        preferenceService.networkCaptureRuleOver = true
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
            networkCaptureData
        )
        assertEquals(0, networkCaptureDataSource.loggedCalls.size)

        // duration = 6000ms should be captured
        service.logNetworkCapturedData(
            "https://embrace.io/changelog",
            "GET",
            200,
            0,
            6000,
            networkCaptureData
        )
        assertEquals(1, networkCaptureDataSource.loggedCalls.size)
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
            networkCaptureData
        )
        assertEquals(1, networkCaptureDataSource.loggedCalls.size)

        service.logNetworkCapturedData(
            "https://embrace.io/changelog",
            "GET",
            404,
            0,
            2000,
            networkCaptureData
        )
        assertEquals(2, networkCaptureDataSource.loggedCalls.size)

        service.logNetworkCapturedData(
            "https://embrace.io/changelog",
            "GET",
            500,
            0,
            2000,
            networkCaptureData
        )
        assertEquals(2, networkCaptureDataSource.loggedCalls.size)
    }

    private fun getService(): EmbraceNetworkCaptureService {
        configService = FakeConfigService(
            networkBehavior = createNetworkBehavior(remoteCfg = cfg)
        )
        return EmbraceNetworkCaptureService(
            sessionIdTracker,
            preferenceService,
            { networkCaptureDataSource },
            configService,
            EmbraceApiUrlBuilder(
                "deviceId",
                "1.0.0",
                FakeInstrumentedConfig()
            ),
            EmbraceSerializer(),
            EmbLoggerImpl()
        )
    }

    private fun getDefaultRule(
        id: String = "123",
        duration: Long = 0,
        expiresIn: Long = 123,
        method: String = "GET, POST",
        maxSize: Long = 102400L,
        maxCount: Int = 5,
        statusCodes: Set<Int> = setOf(200, 404),
        urlRegex: String = "embrace.io/*",
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
