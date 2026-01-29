package io.embrace.android.embracesdk.internal.instrumentation.network

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.createNetworkBehavior
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NetworkCaptureDataSourceTest {

    private lateinit var cfg: RemoteConfig
    private lateinit var configService: FakeConfigService
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var destination: FakeTelemetryDestination
    private val networkCaptureData = HttpNetworkRequest.HttpRequestBody(null, null, null, null, null, null)

    @Before
    fun setUp() {
        cfg = RemoteConfig()
        configService = FakeConfigService(
            networkBehavior = createNetworkBehavior(remoteCfg = cfg)
        )
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
        val rule = getDefaultRule()
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        val result = getService().getNetworkCaptureRules("url", "GET")
        assertEquals(0, result.size)
    }

    @Test
    fun `test capture rule doesn't capture Embrace endpoints`() {
        val rule = getDefaultRule(urlRegex = "https://a-abcde.data.emb-api.com/v2")
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        val result = getService().getNetworkCaptureRules("https://a-abcde.data.emb-api.com/v2/spans", "GET")
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
        val service = getService()

        val result = service.getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertEquals(1, result.size)
        args.store.edit {
            putInt(NetworkCaptureDataSourceImpl.Companion.NETWORK_CAPTURE_RULE_PREFIX_KEY + rule.id, 0)
        }
        val emptyRule = service.getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertEquals(0, emptyRule.size)
    }

    @Test
    fun `test capture rule maxCount is over`() {
        val rule = getDefaultRule()
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        val service = getService()
        args.store.edit {
            putInt(NetworkCaptureDataSourceImpl.Companion.NETWORK_CAPTURE_RULE_PREFIX_KEY + rule.id, 0)
        }
        val emptyRule = service.getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertEquals(0, emptyRule.size)
    }

    @Test
    fun `test capture rule matches URL and method `() {
        val rule = getDefaultRule()
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))
        val result = getService().getNetworkCaptureRules("https://embrace.io/changelog", "GET")
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `test capture rule doesnt match URL and method `() {
        val rule = getDefaultRule(
            method = "POST",
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
        service.recordNetworkRequest(
            HttpNetworkRequest(
                url = "https://embrace.io/changelog",
                httpMethod = "GET",
                statusCode = 200,
                startTime = 0,
                endTime = 2000,
                body = networkCaptureData,
            )
        )
        assertEquals(0, destination.logEvents.size)

        // duration = 6000ms should be captured
        service.recordNetworkRequest(
            HttpNetworkRequest(
                url = "https://embrace.io/changelog",
                httpMethod = "GET",
                statusCode = 200,
                startTime = 0,
                endTime = 6000,
                body = networkCaptureData
            )
        )
        assertEquals(1, destination.logEvents.size)
    }

    @Test
    fun `test capture rule status codes `() {
        val rule = getDefaultRule(statusCodes = setOf(200, 404))
        cfg = RemoteConfig(networkCaptureRules = setOf(rule))

        val service = getService()
        service.recordNetworkRequest(
            HttpNetworkRequest(

                url = "https://embrace.io/changelog",
                httpMethod = "GET",
                statusCode = 200,
                startTime = 0,
                endTime = 2000,
                body = networkCaptureData
            )
        )
        assertEquals(1, destination.logEvents.size)

        service.recordNetworkRequest(
            HttpNetworkRequest(
                url = "https://embrace.io/changelog",
                httpMethod = "GET",
                statusCode = 404,
                startTime = 0,
                endTime = 2000,
                body = networkCaptureData
            )
        )
        assertEquals(2, destination.logEvents.size)

        service.recordNetworkRequest(
            HttpNetworkRequest(
                url = "https://embrace.io/changelog",
                httpMethod = "GET",
                statusCode = 500,
                startTime = 0,
                endTime = 2000,
                body = networkCaptureData
            )
        )
        assertEquals(2, destination.logEvents.size)
    }

    @Test
    fun `test network capture is sent as log`() {
        val dataSource = getService()
        val capturedCall = fakeNetworkCapturedCall()
        dataSource.logNetworkCapturedCall(capturedCall)

        assertEquals(1, destination.logEvents.size)
        val log = destination.logEvents[0]
        assertEquals(SchemaType.NetworkCapturedRequest::class.java, log.schemaType.javaClass)
        assertEquals(100L, capturedCall.duration)
        assertEquals(1713453000L, capturedCall.endTime)
        assertEquals("GET", capturedCall.httpMethod)
        assertEquals("httpbin.*", capturedCall.matchedUrl)
        assertEquals("body", capturedCall.requestBody)
        assertEquals(10, capturedCall.requestBodySize)
        assertEquals("id", capturedCall.networkId)
        assertEquals("query", capturedCall.requestQuery)
        assertEquals(mapOf("query-header" to "value"), capturedCall.requestQueryHeaders)
        assertEquals(5, capturedCall.requestSize)
        assertEquals("response", capturedCall.responseBody)
        assertEquals(8, capturedCall.responseBodySize)
        assertEquals(mapOf("response-header" to "value"), capturedCall.responseHeaders)
        assertEquals(300, capturedCall.responseSize)
        assertEquals(200, capturedCall.responseStatus)
        assertEquals("fake-session-id", capturedCall.sessionId)
        assertEquals(1713452000L, capturedCall.startTime)
        assertEquals("https://httpbin.org/get", capturedCall.url)
        assertEquals("", capturedCall.errorMessage)
        assertEquals("encrypted-payload", capturedCall.encryptedPayload)
    }

    @Test
    fun `test telemetry tracked when network body is truncated`() {
        cfg = RemoteConfig(networkCaptureRules = setOf(getDefaultRule(maxSize = 2)))
        val telemetryService = FakeTelemetryService()
        configService = FakeConfigService(networkBehavior = createNetworkBehavior(remoteCfg = cfg))
        args = FakeInstrumentationArgs(
            application = ApplicationProvider.getApplicationContext(),
            configService = configService,
            telemetryService = telemetryService
        )

        val largeBody = ByteArray(3) { 'a'.code.toByte() }
        NetworkCaptureDataSourceImpl(args).recordNetworkRequest(
            HttpNetworkRequest(
                url = "https://embrace.io/changelog",
                httpMethod = "GET",
                statusCode = 200,
                startTime = 0,
                endTime = 1000,
                body = HttpNetworkRequest.HttpRequestBody(null, null, largeBody, null, largeBody, null)
            )
        )

        assertEquals(2, telemetryService.appliedLimits.size)
        assertEquals("network_body", telemetryService.appliedLimits[0].first)
        assertEquals(AppliedLimitType.TRUNCATE_STRING, telemetryService.appliedLimits[0].second)
        assertEquals("network_body", telemetryService.appliedLimits[1].first)
        assertEquals(AppliedLimitType.TRUNCATE_STRING, telemetryService.appliedLimits[1].second)
    }

    private fun getService(): NetworkCaptureDataSourceImpl {
        configService = FakeConfigService(
            networkBehavior = createNetworkBehavior(remoteCfg = cfg)
        )
        args = FakeInstrumentationArgs(
            application = ApplicationProvider.getApplicationContext(),
            configService = configService
        )
        destination = args.destination
        return NetworkCaptureDataSourceImpl(args,)
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
