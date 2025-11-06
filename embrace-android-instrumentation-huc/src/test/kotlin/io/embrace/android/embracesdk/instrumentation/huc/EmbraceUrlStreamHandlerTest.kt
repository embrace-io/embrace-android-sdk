package io.embrace.android.embracesdk.instrumentation.huc

import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehaviorImpl.Companion.TRACEPARENT_HEADER_NAME
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.net.URL

@Config(sdk = [UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
internal class EmbraceUrlStreamHandlerTest {

    private lateinit var internalApi: FakeInternalNetworkApi

    @Before
    fun setup() {
        internalApi = FakeInternalNetworkApi()
    }

    @Test
    fun `check traceparent is not injected into http request by default`() {
        val url = URL(
            "http",
            "embrace.io",
            1881,
            "insecure.txt",
            EmbraceHttpUrlStreamHandler(
                httpUrlStreamHandler,
                internalApi,
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertNull(connection.getRequestProperty(TRACEPARENT_HEADER_NAME))
    }

    @Test
    fun `check traceparent is not injected into https request by default`() {
        val url = URL(
            "https",
            "embrace.io",
            1881,
            "secure.txt",
            EmbraceHttpsUrlStreamHandler(
                httpsUrlStreamHandler,
                internalApi
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertNull(connection.getRequestProperty(TRACEPARENT_HEADER_NAME))
    }

    @Test
    fun `check traceparent is injected into http request if feature flag is on`() {
        internalApi.internalInterface.networkSpanForwardingEnabled = true
        val url = URL(
            "http",
            "embrace.io",
            1881,
            "insecure.txt",
            EmbraceHttpUrlStreamHandler(
                httpUrlStreamHandler,
                internalApi
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertNotNull(connection.getRequestProperty(TRACEPARENT_HEADER_NAME))
    }

    @Test
    fun `check traceparent is injected into https request if feature flag is on`() {
        internalApi.internalInterface.networkSpanForwardingEnabled = true
        val url = URL(
            "https",
            "embrace.io",
            1881,
            "secure.txt",
            EmbraceHttpsUrlStreamHandler(
                httpsUrlStreamHandler,
                internalApi
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertNotNull(connection.getRequestProperty(TRACEPARENT_HEADER_NAME))
    }

    @Test
    fun `check http connection is intercepted by embrace if sdk is started`() {
        val url = URL(
            "http",
            "embrace.io",
            1881,
            "insecure.txt",
            EmbraceHttpUrlStreamHandler(
                httpUrlStreamHandler,
                internalApi
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertTrue(connection is EmbraceHttpUrlConnectionImpl<*>)
    }

    @Test
    fun `check https connection is intercepted by embrace if sdk is started`() {
        val url = URL(
            "https",
            "embrace.io",
            1881,
            "insecure.txt",
            EmbraceHttpsUrlStreamHandler(
                httpsUrlStreamHandler,
                internalApi
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertTrue(connection is EmbraceHttpsUrlConnectionImpl<*>)
    }

    @Test
    fun `check http connection is not intercepted by embrace if sdk is not started`() {
        internalApi.started = false
        val url = URL(
            "http",
            "embrace.io",
            1881,
            "insecure.txt",
            EmbraceHttpUrlStreamHandler(
                httpUrlStreamHandler,
                internalApi
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertFalse(connection is EmbraceHttpUrlConnectionImpl<*>)
    }

    @Test
    fun `check https connection is not intercepted by embrace if sdk is not started`() {
        internalApi.started = false
        val url = URL(
            "https",
            "embrace.io",
            1881,
            "insecure.txt",
            EmbraceHttpsUrlStreamHandler(
                httpsUrlStreamHandler,
                internalApi
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertFalse(connection is EmbraceHttpsUrlConnectionImpl<*>)
    }

    companion object {
        private val httpUrlStreamHandler =
            HttpUrlConnectionTracker.EmbraceUrlStreamHandlerFactory.newUrlStreamHandler("com.android.okhttp.HttpHandler")
        private val httpsUrlStreamHandler =
            HttpUrlConnectionTracker.EmbraceUrlStreamHandlerFactory.newUrlStreamHandler("com.android.okhttp.HttpsHandler")
    }
}
