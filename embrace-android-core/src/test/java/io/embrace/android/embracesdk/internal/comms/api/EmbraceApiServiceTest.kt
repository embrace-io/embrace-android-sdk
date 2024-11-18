package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeApiClient
import io.embrace.android.embracesdk.fakes.FakeDeliveryCacheManager
import io.embrace.android.embracesdk.fakes.FakePendingApiCallsSender
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.TypeUtils
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.network.http.HttpMethod
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

internal class EmbraceApiServiceTest {

    private val serializer = EmbraceSerializer()
    private val logPayload = Envelope(data = LogPayload())
    private val logType = TypeUtils.parameterizedType(Envelope::class, LogPayload::class)

    private lateinit var apiUrlBuilder: ApiUrlBuilder
    private lateinit var fakeApiClient: FakeApiClient
    private lateinit var fakeCacheManager: DeliveryCacheManager
    private lateinit var testScheduledExecutor: BlockingScheduledExecutorService
    private lateinit var apiService: EmbraceApiService
    private lateinit var fakePendingApiCallsSender: FakePendingApiCallsSender

    @Before
    fun setUp() {
        apiUrlBuilder = EmbraceApiUrlBuilder(
            deviceId = fakeDeviceId,
            appVersionName = fakeAppVersionName,
            instrumentedConfig = FakeInstrumentedConfig()
        )
        fakeApiClient = FakeApiClient()
        testScheduledExecutor = BlockingScheduledExecutorService(blockingMode = false)
        fakeCacheManager = FakeDeliveryCacheManager()
        fakePendingApiCallsSender = FakePendingApiCallsSender()
        initApiService()
    }

    @After
    fun tearDown() {
        Endpoint.values().forEach {
            it.limiter.clearRateLimit()
        }
    }

    @Test
    fun `send v2 session`() {
        fakeApiClient.queueResponse(successfulPostResponse)
        val payload = "".toByteArray(Charsets.UTF_8)
        var finished = false
        apiService.sendSession({ it.write(payload) }) { finished = true }
        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v2/spans",
            expectedPayload = payload
        )
        assertTrue(finished)
    }

    @Test
    fun `send logs envelope is as expected`() {
        val logsEnvelope = Envelope(
            data = LogPayload(
                logs = listOf(
                    Log(
                        traceId = "traceId",
                        spanId = "spanId",
                        timeUnixNano = 1234567890,
                        severityText = "severityText",
                        severityNumber = 1,
                        body = "a message",
                        attributes = listOf(Attribute("key", "value"))
                    )
                )
            )
        )

        fakeApiClient.queueResponse(successfulPostResponse)
        apiService.sendLogEnvelope(logsEnvelope)

        val type: ParameterizedType = TypeUtils.parameterizedType(Envelope::class, LogPayload::class)

        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v2/logs",
            expectedPayload = getGenericsExpectedPayloadSerialized(logsEnvelope, type)
        )
    }

    @Test
    fun `save logs envelope is as expected`() {
        val logsEnvelope = Envelope(
            data = LogPayload(
                logs = listOf(
                    Log(
                        traceId = "traceId",
                        spanId = "spanId",
                        timeUnixNano = 1234567890,
                        severityText = "severityText",
                        severityNumber = 1,
                        body = "a message",
                        attributes = listOf(Attribute("key", "value"))
                    )
                )
            )
        )

        apiService.saveLogEnvelope(logsEnvelope)

        assertEquals(0, fakeApiClient.sentRequests.size)
        assertEquals(1, fakePendingApiCallsSender.retryQueue.size)

        val request = fakePendingApiCallsSender.retryQueue.single().first
        val payload = fakePendingApiCallsSender.retryQueue.single().second
        assertEquals("https://a-$fakeAppId.data.emb-api.com/v2/logs", request.url.url)
        val type: ParameterizedType = TypeUtils.parameterizedType(Envelope::class, LogPayload::class)
        assertArrayEquals(getGenericsExpectedPayloadSerialized(logsEnvelope, type), payload)
    }

    @Test
    fun `network request runnable is used`() {
        initApiService()
        val payload = "{}".toByteArray(Charsets.UTF_8)
        apiService.sendSession({ it.write(payload) }) {}
        assertEquals(1, testScheduledExecutor.submitCount)
    }

    @Test
    fun `unsuccessful requests are queued for later`() {
        apiService.onNetworkConnectivityStatusChanged(NetworkStatus.NOT_REACHABLE)
        apiService.sendLogEnvelope(logPayload)
        assertEquals(0, fakeApiClient.sentRequests.size)
        val request = fakePendingApiCallsSender.retryQueue.single().first
        assertEquals("https://a-$fakeAppId.data.emb-api.com/v2/logs", request.url.url)
    }

    @Test
    fun `test that requests returning a TooManyRequests response, saves and schedule a pending api call`() {
        val response = ApiResponse.TooManyRequests(
            endpoint = Endpoint.LOGS,
            retryAfter = 3
        )
        fakeApiClient.queueResponse(response)

        assertEquals(0, fakePendingApiCallsSender.retryQueue.size)
        assertFalse(fakePendingApiCallsSender.didScheduleApiCall)
        apiService.sendLogEnvelope(logPayload)

        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v2/logs",
            expectedPayload = getExpectedPayloadSerialized(logPayload, logType)
        )
        assertEquals(1, fakePendingApiCallsSender.retryQueue.size)
        assertTrue(fakePendingApiCallsSender.didScheduleApiCall)
    }

    @Test
    fun `test that requests returning a Incomplete response, saves and schedule a pending api call`() {
        val response = ApiResponse.Incomplete(Throwable())
        fakeApiClient.queueResponse(response)

        assertEquals(0, fakePendingApiCallsSender.retryQueue.size)
        assertFalse(fakePendingApiCallsSender.didScheduleApiCall)

        apiService.sendLogEnvelope(logPayload)

        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v2/logs",
            expectedPayload = getExpectedPayloadSerialized(logPayload, logType)
        )
        assertEquals(1, fakePendingApiCallsSender.retryQueue.size)
        assertTrue(fakePendingApiCallsSender.didScheduleApiCall)
    }

    @Test
    fun `test that requests returning a Failure response, do not save a pending api call`() {
        val response = ApiResponse.Failure(
            code = 400,
            headers = emptyMap()
        )
        fakeApiClient.queueResponse(response)

        assertEquals(0, fakePendingApiCallsSender.retryQueue.size)
        assertFalse(fakePendingApiCallsSender.didScheduleApiCall)

        apiService.sendLogEnvelope(logPayload)

        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v2/logs",
            expectedPayload = getExpectedPayloadSerialized(logPayload, logType)
        )
        assertEquals(0, fakePendingApiCallsSender.retryQueue.size)
        assertFalse(fakePendingApiCallsSender.didScheduleApiCall)
    }

    @Test
    fun `test that requests returning a PayloadTooLarge response, do not save a pending api call`() {
        val response = ApiResponse.PayloadTooLarge
        fakeApiClient.queueResponse(response)

        assertEquals(0, fakePendingApiCallsSender.retryQueue.size)
        assertFalse(fakePendingApiCallsSender.didScheduleApiCall)

        apiService.sendLogEnvelope(logPayload)

        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v2/logs",
            expectedPayload = getExpectedPayloadSerialized(logPayload, logType)
        )
        assertEquals(0, fakePendingApiCallsSender.retryQueue.size)
        assertFalse(fakePendingApiCallsSender.didScheduleApiCall)
    }

    @Test
    fun `test that requests returning a Success response, do not save a pending api call`() {
        val response = ApiResponse.Success(
            headers = emptyMap(),
            body = ""
        )
        fakeApiClient.queueResponse(response)

        assertEquals(0, fakePendingApiCallsSender.retryQueue.size)
        assertFalse(fakePendingApiCallsSender.didScheduleApiCall)

        apiService.sendLogEnvelope(logPayload)

        verifyOnlyRequest(
            expectedUrl = "https://a-$fakeAppId.data.emb-api.com/v2/logs",
            expectedPayload = getExpectedPayloadSerialized(logPayload, logType)
        )
        assertEquals(0, fakePendingApiCallsSender.retryQueue.size)
        assertFalse(fakePendingApiCallsSender.didScheduleApiCall)
    }

    @Test
    fun `test that requests to rate limited endpoint, do not execute the request and save a pending api call`() {
        val endpoint = Endpoint.LOGS
        with(endpoint.limiter) {
            updateRateLimitStatus()
            scheduleRetry(
                worker = BackgroundWorker(
                    testScheduledExecutor
                ),
                retryAfter = 3,
                retryMethod = { }
            )
        }

        apiService.sendLogEnvelope(logPayload)

        assertEquals(0, fakeApiClient.sentRequests.size)
        assertEquals(1, fakePendingApiCallsSender.retryQueue.size)
        // assert that the pending api call was not scheduled, since all pending api calls
        // are executed once the rate limit for the endpoint is lifted.
        assertFalse(fakePendingApiCallsSender.didScheduleApiCall)
    }

    @Test
    fun `test that requests with no connection, do not execute the request and save a pending api call`() {
        apiService.onNetworkConnectivityStatusChanged(NetworkStatus.NOT_REACHABLE)
        apiService.sendLogEnvelope(logPayload)

        assertEquals(0, fakeApiClient.sentRequests.size)
        assertEquals(1, fakePendingApiCallsSender.retryQueue.size)
        // assert that the pending api call was not scheduled, since all pending api calls
        // are executed once the network connection is restored.
        assertFalse(fakePendingApiCallsSender.didScheduleApiCall)
    }

    private inline fun <reified T> getExpectedPayloadSerialized(payload: T, type: Type): ByteArray {
        val os = ByteArrayOutputStream()
        ConditionalGzipOutputStream(os).use {
            serializer.toJson(payload, type, it)
        }
        return os.toByteArray()
    }

    private inline fun <reified T> getGenericsExpectedPayloadSerialized(
        payload: T,
        parameterizedType: ParameterizedType,
    ): ByteArray {
        val os = ByteArrayOutputStream()
        ConditionalGzipOutputStream(os).use {
            serializer.toJson(payload, parameterizedType, it)
        }
        return os.toByteArray()
    }

    private fun verifyOnlyRequest(
        expectedUrl: String,
        expectedMethod: HttpMethod = HttpMethod.POST,
        expectedEtag: String? = null,
        expectedPayload: ByteArray? = null,
    ) {
        assertEquals(1, fakeApiClient.sentRequests.size)
        with(fakeApiClient.sentRequests[0].first) {
            assertEquals("application/json", contentType)
            assertEquals("application/json", accept)
            assertNull(acceptEncoding)
            assertEquals("gzip", contentEncoding)
            assertEquals(fakeAppId, appId)
            assertEquals(fakeDeviceId, deviceId)
            assertEquals(expectedUrl, url.url)
            assertEquals(expectedMethod, httpMethod)
            assertEquals(expectedEtag, eTag)
        }

        expectedPayload?.let {
            assertArrayEquals(it, fakeApiClient.sentRequests[0].second?.readBytes())
        } ?: assertNull(fakeApiClient.sentRequests[0].second)
    }

    private fun initApiService() {
        apiService = EmbraceApiService(
            apiClient = fakeApiClient,
            serializer = serializer,
            priorityWorker = PriorityWorker(testScheduledExecutor),
            pendingApiCallsSender = fakePendingApiCallsSender,
            lazyDeviceId = lazy { fakeDeviceId },
            appId = fakeAppId,
            urlBuilder = apiUrlBuilder
        )
        apiService.onNetworkConnectivityStatusChanged(NetworkStatus.WIFI)
    }

    companion object {
        private const val fakeAppId = "abcde"
        private const val fakeDeviceId = "ajflkadsflkadslkfjds"
        private const val fakeAppVersionName = "6.1.0"
        private val successfulPostResponse = ApiResponse.Success(
            headers = emptyMap(),
            body = ""
        )
    }
}
