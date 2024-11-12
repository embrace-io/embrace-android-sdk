package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeBaseUrlConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.OkHttpRemoteConfigSource
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OkHttpRemoteConfigSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var urlBuilder: EmbraceApiUrlBuilder
    private lateinit var source: OkHttpRemoteConfigSource

    private val remoteConfig = RemoteConfig(
        backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)
    )

    private lateinit var configResponseBuffer: Buffer

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            protocols = listOf(Protocol.HTTP_1_1, Protocol.HTTP_2)
            start()
        }
        val baseUrl = server.url("api").toString()
        client = OkHttpClient.Builder().build()
        urlBuilder = EmbraceApiUrlBuilder(
            "deviceId",
            "1.0.0",
            FakeInstrumentedConfig(
                baseUrls = FakeBaseUrlConfig(
                    configImpl = baseUrl,
                )
            )
        )

        // serialize the config response
        configResponseBuffer = Buffer()
        val gzipSink = GzipSink(configResponseBuffer).buffer()
        TestPlatformSerializer().toJson(
            remoteConfig,
            RemoteConfig::class.java,
            gzipSink.outputStream()
        )
        source = OkHttpRemoteConfigSource(client, urlBuilder, TestPlatformSerializer())
    }

    @Test
    fun `test config 2xx`() {
        val (cfg, request) = executeRequest(
            MockResponse().setResponseCode(200).setBody(configResponseBuffer)
        )
        assertConfigRequestReceived(request)
        assertConfigResponseDeserialized(cfg)
    }

    @Test
    fun `test config 4xx`() {
        val (cfg, request) = executeRequest(
            MockResponse().setResponseCode(400)
        )
        assertConfigRequestReceived(request)
        assertConfigResponseNotDeserialized(cfg)
    }

    @Test
    fun `test config 5xx`() {
        val (cfg, request) = executeRequest(
            MockResponse().setResponseCode(500)
        )
        assertConfigRequestReceived(request)
        assertConfigResponseNotDeserialized(cfg)
    }

    @Test
    fun `test no response from server`() {
        client = client.newBuilder().callTimeout(1, TimeUnit.MILLISECONDS).build()
        val (cfg, request) = executeRequest(null)
        assertConfigRequestNotReceived(request)
        assertConfigResponseNotDeserialized(cfg)
    }

    @Test
    fun `test invalid response from server`() {
        val (cfg, request) = executeRequest(
            MockResponse().setResponseCode(200).setBody("{")
        )
        assertConfigRequestReceived(request)
        assertConfigResponseNotDeserialized(cfg)
    }

    @Test
    fun `test etag header respected`() {
        val etagValue = "attempt_1"
        val (cfg, request) = executeRequest(
            MockResponse().setResponseCode(200)
                .setBody(configResponseBuffer)
                .setHeader("ETag", etagValue)
        )
        assertConfigRequestReceived(request)
        assertNull(request?.getHeader("If-None-Match"))
        assertConfigResponseDeserialized(cfg)

        // second request with etag
        val (secondCfg, secondRequest) = executeRequest(
            MockResponse().setResponseCode(304)
                .setHeader("ETag", etagValue)
        )
        assertConfigRequestReceived(secondRequest)
        assertEquals(etagValue, secondRequest?.getHeader("If-None-Match"))
        assertConfigResponseNotDeserialized(secondCfg)
    }

    private fun executeRequest(response: MockResponse?): Pair<RemoteConfig?, RecordedRequest?> {
        if (response == null) {
            return Pair(null, null)
        }
        server.enqueue(response)
        val cfg = source.getConfig()
        val request = pollRequest()
        CallData(request, cfg)
        return Pair(cfg, request)
    }

    private fun assertEmbraceHeadersAdded(request: RecordedRequest?) {
        val headers = request?.headers?.toMap() ?: error("Request headers cannot be null")
        assertEquals("application/json", headers["Accept"])
        assertEquals("application/json", headers["Content-Type"])
        assertEquals("gzip", headers["Accept-Encoding"])
        assertEquals("abcde", headers["X-EM-AID"])
        assertEquals("deviceId", headers["X-EM-DID"])
    }

    private fun assertConfigRequestNotReceived(request: RecordedRequest?) {
        assertNull(request)
    }

    private fun assertConfigRequestReceived(request: RecordedRequest?) {
        checkNotNull(request)
        assertEmbraceHeadersAdded(request)
        val requestUrl = request.requestUrl?.toUrl() ?: error("Request URL cannot be null")
        assertEquals("/api/v2/config", requestUrl.path)
        assertEquals("appId=abcde&osVersion=21.0.0&appVersion=1.0.0&deviceId=deviceId", requestUrl.query)
    }

    private fun assertConfigResponseDeserialized(cfg: RemoteConfig?) {
        checkNotNull(cfg)
        assertEquals(remoteConfig.backgroundActivityConfig, cfg.backgroundActivityConfig)
    }

    private fun assertConfigResponseNotDeserialized(cfg: RemoteConfig?) {
        assertNull(cfg)
    }

    private data class CallData(
        val request: RecordedRequest?,
        val deserializedConfig: RemoteConfig?,
    )

    private fun pollRequest(): RecordedRequest? = server.takeRequest(1, TimeUnit.SECONDS)
}
