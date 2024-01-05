package io.embrace.android.embracesdk.internal.network.http

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.config.behavior.NetworkSpanForwardingBehavior.Companion.TRACEPARENT_HEADER_NAME
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.net.URL

@Config(sdk = [TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class EmbraceUrlStreamHandlerTest {
    private lateinit var mockEmbrace: Embrace
    private lateinit var mockInternalInterface: EmbraceInternalInterface
    private lateinit var capturedEmbraceNetworkRequest: CapturingSlot<EmbraceNetworkRequest>
    private var isNetworkSpanForwardingEnabled = false

    @Before
    fun setup() {
        mockEmbrace = mockk(relaxed = true)
        mockInternalInterface = mockk(relaxed = true)
        every { mockInternalInterface.isNetworkSpanForwardingEnabled() } answers { isNetworkSpanForwardingEnabled }
        capturedEmbraceNetworkRequest = slot()
        every { mockEmbrace.recordNetworkRequest(capture(capturedEmbraceNetworkRequest)) } answers { }
        every { mockEmbrace.internalInterface } answers { mockInternalInterface }
        every { mockEmbrace.generateW3cTraceparent() } answers { TRACEPARENT }
        isNetworkSpanForwardingEnabled = false
    }

    @Test
    fun `check traceheader is not injected into http request by default`() {
        val url = URL(
            "http",
            "embrace.io",
            1881,
            "insecure.txt",
            EmbraceHttpUrlStreamHandler(
                httpUrlStreamHandler,
                mockEmbrace
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertNull(connection.getRequestProperty(TRACEPARENT_HEADER_NAME))
    }

    @Test
    fun `check traceheader is not injected into https request by default`() {
        val url = URL(
            "https",
            "embrace.io",
            1881,
            "secure.txt",
            EmbraceHttpsUrlStreamHandler(
                httpsUrlStreamHandler,
                mockEmbrace
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertNull(connection.getRequestProperty(TRACEPARENT_HEADER_NAME))
    }

    @Test
    fun `check traceheader is injected into http request if feature flag is on`() {
        isNetworkSpanForwardingEnabled = true
        val url = URL(
            "http",
            "embrace.io",
            1881,
            "insecure.txt",
            EmbraceHttpUrlStreamHandler(
                httpUrlStreamHandler,
                mockEmbrace
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertEquals(TRACEPARENT, connection.getRequestProperty(TRACEPARENT_HEADER_NAME))
    }

    @Test
    fun `check traceheader is injected into https request if feature flag is on`() {
        isNetworkSpanForwardingEnabled = true
        val url = URL(
            "https",
            "embrace.io",
            1881,
            "secure.txt",
            EmbraceHttpsUrlStreamHandler(
                httpsUrlStreamHandler,
                mockEmbrace
            )
        )
        val connection = checkNotNull(url.openConnection())
        assertEquals(TRACEPARENT, connection.getRequestProperty(TRACEPARENT_HEADER_NAME))
    }

    companion object {
        private const val TRACEPARENT = "00-3c72a77a7b51af6fb3778c06d4c165ce-4c1d710fffc88e35-01"
        private val httpUrlStreamHandler = EmbraceUrlStreamHandlerFactory.newUrlStreamHandler("com.android.okhttp.HttpHandler")
        private val httpsUrlStreamHandler = EmbraceUrlStreamHandlerFactory.newUrlStreamHandler("com.android.okhttp.HttpsHandler")
    }
}
