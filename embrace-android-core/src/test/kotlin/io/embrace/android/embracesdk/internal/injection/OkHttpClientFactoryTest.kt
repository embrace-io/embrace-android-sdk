package io.embrace.android.embracesdk.internal.injection

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

internal class OkHttpClientFactoryTest {

    @Test
    fun `evicting connections does not construct the client`() {
        val client = lazyOkHttpClient()
        client.evictConnections()
        assertFalse(client.isInitialized())
    }

    @Test
    fun `evicting connections closes idle pooled connections`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val client = lazyOkHttpClient()
            client.value.newCall(Request.Builder().url(server.url("/")).build()).execute().use { it.body?.string() }
            assertEquals(1, client.value.connectionPool.idleConnectionCount())

            client.evictConnections()
            assertEquals(0, client.value.connectionPool.connectionCount())
        }
    }
}
