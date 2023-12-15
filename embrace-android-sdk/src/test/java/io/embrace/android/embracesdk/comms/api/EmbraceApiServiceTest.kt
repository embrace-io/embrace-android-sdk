package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.comms.api.ApiClient.Companion.NO_HTTP_RESPONSE
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeApiClient
import io.embrace.android.embracesdk.fakes.FakeDeliveryCacheManager
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakePendingApiCallsSender
import io.embrace.android.embracesdk.fakes.FakeRateLimitHandler
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.worker.NetworkRequestRunnable
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ScheduledExecutorService

internal class EmbraceApiServiceTest {

    private val serializer = EmbraceSerializer()

    private lateinit var apiUrlBuilder: ApiUrlBuilder
    private lateinit var fakeApiClient: FakeApiClient
    private lateinit var fakeCacheManager: DeliveryCacheManager
    private lateinit var testScheduledExecutor: ScheduledExecutorService
    private lateinit var networkConnectivityService: FakeNetworkConnectivityService
    private lateinit var cachedConfig: CachedConfig
    private lateinit var apiService: EmbraceApiService
    private lateinit var fakePendingApiCallsSender: FakePendingApiCallsSender
    private lateinit var fakeRateLimitHandler: FakeRateLimitHandler

    @Before
    fun setUp() {
        apiUrlBuilder = EmbraceApiUrlBuilder(
            coreBaseUrl = "https://a-$fakeAppId.data.emb-api.com",
            configBaseUrl = "https://a-$fakeAppId.config.emb-api.com",
            appId = fakeAppId,
            lazyDeviceId = lazy { fakeDeviceId },
            lazyAppVersionName = lazy { fakeAppVersionName }
        )
        fakeApiClient = FakeApiClient()
        cachedConfig = CachedConfig(
            remoteConfig = RemoteConfig()
        )
        networkConnectivityService = FakeNetworkConnectivityService()
        testScheduledExecutor = BlockingScheduledExecutorService(blockingMode = false)
        fakeCacheManager = FakeDeliveryCacheManager()
        fakePendingApiCallsSender = FakePendingApiCallsSender()
        fakeRateLimitHandler = FakeRateLimitHandler()
        initApiService()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `test getConfig returns correct values in Response`() {
        fakeApiClient.queueResponse(
            ApiResponse.Success(
                headers = emptyMap(),
                body = defaultConfigResponseBody
            )
        )

        val remoteConfig = apiService.getConfig()

        // verify a few fields were serialized correctly.
        checkNotNull(remoteConfig)
        assertTrue(checkNotNull(remoteConfig.sessionConfig?.isEnabled))
        assertFalse(checkNotNull(remoteConfig.sessionConfig?.endAsync))
        assertEquals(100, remoteConfig.threshold)
    }

    @Test(expected = IllegalStateException::class)
    fun `getConfig throws an exception when receiving ApiResponse_Incomplete`() {
        val incompleteResponse: ApiResponse.Incomplete = ApiResponse.Incomplete(
            IllegalStateException("Connection failed")
        )
        fakeApiClient.queueResponse(incompleteResponse)
        apiService.getConfig()
    }

    @Test
    fun `cached remote config returned when 304 received`() {
        fakeApiClient.queueResponse(
            ApiResponse.NotModified
        )
        assertEquals(cachedConfig.remoteConfig, apiService.getConfig())
    }

    @Test
    fun `getConfig did not complete returns a null config`() {
        fakeApiClient.queueResponse(
            ApiResponse.Failure(
                code = NO_HTTP_RESPONSE,
                headers = emptyMap()
            )
        )
        assertNull(apiService.getConfig())
    }

    @Test
    fun `getConfig results in unexpected response code returns a null config`() {
        fakeApiClient.queueResponse(
            ApiResponse.Failure(
                code = 400,
                headers = emptyMap()
            )
        )
        assertNull(apiService.getConfig())
    }

    @Test
    fun testGetConfigWithMatchingEtag() {
        val cfg = RemoteConfig()
        cachedConfig = CachedConfig(cfg, "my_etag")
        fakeApiClient.queueResponse(
            ApiResponse.NotModified
        )
        val remoteConfig = apiService.getConfig()
        assertSame(cfg, remoteConfig)
    }

    @Test
    fun `getCacheConfig returns what the provider provides`() {
        assertEquals(apiService.getCachedConfig(), cachedConfig)
    }

    @Test
    fun `send log request is as expected`() {
        fakeApiClient.queueResponse(successfulPostResponse)
        val event = EventMessage(
            event = Event(
                eventId = "event-id",
                messageId = "message-id",
                type = EmbraceEvent.Type.ERROR_LOG
            )
        )
        apiService.sendLog(event)
        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v1/log/logging",
            expectedLogId = "el:message-id",
            expectedPayload = serializer.toJson(event).toByteArray()
        )
    }

    @Test
    fun `send application exit info request is as expected`() {
        fakeApiClient.queueResponse(successfulPostResponse)
        val blob = BlobMessage()
        apiService.sendAEIBlob(blob)
        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v1/log/blobs",
            expectedPayload = serializer.toJson(blob).toByteArray()
        )
    }

    @Test
    fun `send network request is as expected`() {
        fakeApiClient.queueResponse(successfulPostResponse)
        val networkEvent: NetworkEvent = mockk(relaxed = true)
        every { networkEvent.eventId } answers { "network-event-id" }
        apiService.sendNetworkCall(networkEvent)
        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v1/log/network",
            expectedLogId = "n:network-event-id",
            expectedPayload = serializer.toJson(networkEvent).toByteArray()
        )
    }

    @Test
    fun `send event request is as expected`() {
        fakeApiClient.queueResponse(successfulPostResponse)
        val event = EventMessage(
            event = Event(
                eventId = "event-id",
                type = EmbraceEvent.Type.END
            )
        )
        apiService.sendEvent(event)
        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v1/log/events",
            expectedEventId = "e:event-id",
            expectedPayload = serializer.toJson(event).toByteArray()
        )
    }

    @Test
    fun `send crash request is as expected`() {
        val crash = EventMessage(
            event = Event(
                eventId = "crash-id",
                activeEventIds = listOf("event-1", "event-2"),
                type = EmbraceEvent.Type.CRASH
            )
        )
        apiService.sendCrash(crash)
        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v1/log/events",
            expectedEventId = "c:event-1,event-2",
            expectedPayload = serializer.toJson(crash).toByteArray()
        )
    }

    @Test
    fun `send session is as expected`() {
        fakeApiClient.queueResponse(successfulPostResponse)
        val payload = "{}".toByteArray(Charsets.UTF_8)
        var finished = false
        apiService.sendSession(payload) { finished = true }
        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v1/log/sessions",
            expectedPayload = payload
        )
        assertTrue(finished)
    }

    @Test
    fun `validate all API endpoint URLs`() {
        EmbraceApiService.Companion.Endpoint.values().forEach {
            assertEquals(
                "https://a-$fakeAppId.data.emb-api.com/v1/log/${it.path}",
                apiUrlBuilder.getEmbraceUrlWithSuffix(it.path)
            )
        }
    }

    @Test
    fun `network request runnable is used`() {
        testScheduledExecutor = mockk(relaxed = true)
        initApiService()
        val payload = "{}".toByteArray(Charsets.UTF_8)
        apiService.sendSession(payload) {}
        verify(exactly = 1) { testScheduledExecutor.submit(any<NetworkRequestRunnable>()) }
    }

    private fun verifyOnlyRequest(
        expectedUrl: String,
        expectedMethod: HttpMethod = HttpMethod.POST,
        expectedEventId: String? = null,
        expectedLogId: String? = null,
        expectedEtag: String? = null,
        expectedPayload: ByteArray? = null
    ) {
        assertEquals(1, fakeApiClient.sentRequests.size)
        with(fakeApiClient.sentRequests[0].first) {
            assertEquals("application/json", contentType)
            assertEquals("application/json", accept)
            assertNull(acceptEncoding)
            assertEquals("gzip", contentEncoding)
            assertEquals(fakeAppId, appId)
            assertEquals(fakeDeviceId, deviceId)
            assertEquals(expectedEventId, eventId)
            assertEquals(expectedLogId, logId)
            assertEquals(expectedUrl, url.toString())
            assertEquals(expectedMethod, httpMethod)
            assertEquals(expectedEtag, eTag)
        }

        expectedPayload?.let {
            assertArrayEquals(it, fakeApiClient.sentRequests[0].second?.readBytes())
        } ?: assertNull(fakeApiClient.sentRequests[0].second)
    }

    private fun initApiService() {
        networkConnectivityService.networkStatus = NetworkStatus.WIFI
        apiService = EmbraceApiService(
            apiClient = fakeApiClient,
            serializer = serializer,
            cachedConfigProvider = { _, _ -> cachedConfig },
            logger = InternalEmbraceLogger(),
            executorService = testScheduledExecutor,
            cacheManager = fakeCacheManager,
            lazyDeviceId = lazy { fakeDeviceId },
            appId = fakeAppId,
            pendingApiCallsSender = fakePendingApiCallsSender,
            rateLimitHandler = fakeRateLimitHandler,
            urlBuilder = apiUrlBuilder,
            networkConnectivityService = networkConnectivityService
        )
    }

    companion object {
        private const val fakeAppId = "A1B2C"
        private const val fakeDeviceId = "ajflkadsflkadslkfjds"
        private const val fakeAppVersionName = "6.1.0"
        private val defaultConfigResponseBody =
            ResourceReader.readResourceAsText("remote_config_response.json")
        private val successfulPostResponse = ApiResponse.Success(
            headers = emptyMap(),
            body = ""
        )
    }
}
