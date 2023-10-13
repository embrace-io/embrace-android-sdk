package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceApiServiceTest {

    private lateinit var mockApiClient: ApiClient
    private lateinit var mockUrlBuilder: ApiUrlBuilder
    private lateinit var cachedConfig: CachedConfig
    private lateinit var apiService: ApiService

    @Before
    fun setUp() {
        mockApiClient = mockk()
        mockUrlBuilder = mockk<ApiUrlBuilder> {
            every { getConfigUrl() } returns "https://test.com"
        }
        cachedConfig = CachedConfig(
            config = null,
            eTag = null
        )
        apiService = EmbraceApiService(
            apiClientProvider = { mockApiClient },
            urlBuilder = mockUrlBuilder,
            serializer = EmbraceSerializer(),
            cachedConfigProvider = { _, _ -> cachedConfig },
            logger = mockk(relaxed = true)
        )
    }

    @Test
    fun `test getConfig returns correct values in Response`() {
        val json = ResourceReader.readResourceAsText("remote_config_response.json")
        every { mockApiClient.executeGet(any()) } returns ApiResponse(
            statusCode = 200,
            body = json,
            headers = emptyMap()
        )
        val remoteConfig = apiService.getConfig()

        // verify a few fields were serialized correctly.
        checkNotNull(remoteConfig)
        assertTrue(checkNotNull(remoteConfig.sessionConfig?.isEnabled))
        assertFalse(checkNotNull(remoteConfig.sessionConfig?.endAsync))
        assertEquals(100, remoteConfig.threshold)
    }

    @Test(expected = IllegalStateException::class)
    fun `test getConfig rethrows an exception thrown by apiClient`() {
        every { mockApiClient.executeGet(any()) } throws IllegalStateException("Test exception message")

        // exception will be thrown and caught by this test's annotation
        apiService.getConfig()
    }

    @Test
    fun testGetConfigWithMatchingEtag() {
        val cfg = RemoteConfig()
        cachedConfig = CachedConfig(cfg, "my_etag")
        every { mockApiClient.executeGet(any()) } returns ApiResponse(
            statusCode = 304,
            body = "",
            headers = emptyMap()
        )

        val remoteConfig = apiService.getConfig()
        assertSame(cfg, remoteConfig)
    }
}
