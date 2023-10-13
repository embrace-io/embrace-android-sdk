package io.embrace.android.embracesdk.network.http

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.behavior.NetworkSpanForwardingBehavior.Companion.TRACEPARENT_HEADER_NAME
import io.embrace.android.embracesdk.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeNetworkSpanForwardingBehavior
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
    private lateinit var fakeConfigService: ConfigService
    private lateinit var capturedEmbraceNetworkRequest: CapturingSlot<EmbraceNetworkRequest>
    private lateinit var remoteNetworkSpanForwardingConfig: NetworkSpanForwardingRemoteConfig

    @Before
    fun setup() {
        mockEmbrace = mockk(relaxed = true)
        capturedEmbraceNetworkRequest = slot()
        remoteNetworkSpanForwardingConfig = NetworkSpanForwardingRemoteConfig(pctEnabled = 0f)
        fakeConfigService = FakeConfigService(
            networkSpanForwardingBehavior = fakeNetworkSpanForwardingBehavior(
                remoteConfig = { remoteNetworkSpanForwardingConfig }
            )
        )
        every { mockEmbrace.recordNetworkRequest(capture(capturedEmbraceNetworkRequest)) } answers { }
        every { mockEmbrace.configService } answers { fakeConfigService }
        every { mockEmbrace.generateW3cTraceparent() } answers { TRACEPARENT }
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
        remoteNetworkSpanForwardingConfig = NetworkSpanForwardingRemoteConfig(pctEnabled = 100f)
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
        remoteNetworkSpanForwardingConfig = NetworkSpanForwardingRemoteConfig(pctEnabled = 100f)
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
