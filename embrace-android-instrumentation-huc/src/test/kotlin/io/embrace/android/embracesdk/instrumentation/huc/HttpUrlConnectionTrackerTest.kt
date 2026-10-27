package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.fakes.FakeEmbraceInternalInterface
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehaviorImpl.Companion.TRACEPARENT_HEADER_NAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URL

internal class HttpUrlConnectionTrackerTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var fakeNetworkingApi: NetworkRequestApi
    private lateinit var fakeInternalInterface: EmbraceInternalInterface

    @Before
    fun setup() {
        args = FakeInstrumentationArgs(errorHandler = FakeErrorHandler(false))
        fakeNetworkingApi = FakeNetworkRequestApi()
        fakeInternalInterface = FakeEmbraceInternalInterface()
    }

    @Test
    fun `initialization works correctly`() {
        assertEquals(NoopInternalNetworkApi, HttpUrlConnectionTracker.getInternalNetworkApi())
        assertFalse(EmbraceUrlStreamHandler.enableRequestSizeCapture)
        HttpUrlConnectionTracker.registerUrlStreamHandlerFactory(
            requestContentLengthCaptureEnabled = true,
            instrumentationArgs = args,
            networkRequestDataSource = null,
            networkCaptureDataSource = null,
        )
        assertNotEquals(NoopInternalNetworkApi, HttpUrlConnectionTracker.getInternalNetworkApi())
        assertTrue(EmbraceUrlStreamHandler.enableRequestSizeCapture)
        HttpUrlConnectionTracker.registerUrlStreamHandlerFactory(
            requestContentLengthCaptureEnabled = false,
            instrumentationArgs = args,
            networkRequestDataSource = null,
            networkCaptureDataSource = null,
        )
        assertFalse(EmbraceUrlStreamHandler.enableRequestSizeCapture)

        HttpUrlConnectionTracker.reset()
        assertEquals(NoopInternalNetworkApi, HttpUrlConnectionTracker.getInternalNetworkApi())
    }

    /**
     * Stream handlers are created while capture is registered and then cached, by the handler factory and by [URL].
     * Once capture is reset (the SDK was disabled), connections they open must not get a traceparent injected.
     */
    @Test
    fun `handler created before reset does not inject traceparent after reset`() {
        args.configService.networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(networkSpanForwardingEnabled = true)
        HttpUrlConnectionTracker.registerUrlStreamHandlerFactory(
            requestContentLengthCaptureEnabled = false,
            instrumentationArgs = args,
            networkRequestDataSource = null,
            networkCaptureDataSource = null,
        )
        val handler = EmbraceHttpsUrlStreamHandler(FakeHttpsURLStreamHandler())
        HttpUrlConnectionTracker.reset()

        val connection = URL("https", "embrace.io", 1881, "secure.txt", handler).openConnection()
        assertNull(connection.getRequestProperty(TRACEPARENT_HEADER_NAME))
    }
}
