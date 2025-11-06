package io.embrace.android.embracesdk.instrumentation.huc

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbraceInternalInterface
import io.embrace.android.embracesdk.fakes.FakeInstrumentationApi
import io.embrace.android.embracesdk.fakes.FakeSdkStateApi
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class HttpUrlConnectionTrackerTest {
    private lateinit var fakeSdkStateApi: SdkStateApi
    private lateinit var fakeInstrumentationApi: InstrumentationApi
    private lateinit var fakeNetworkingApi: NetworkRequestApi
    private lateinit var fakeInternalInterface: EmbraceInternalInterface

    @Before
    fun setup() {
        fakeSdkStateApi = FakeSdkStateApi()
        fakeInstrumentationApi = FakeInstrumentationApi()
        fakeNetworkingApi = FakeNetworkRequestApi()
        fakeInternalInterface = FakeEmbraceInternalInterface()
    }

    @Test
    fun `initialization works correctly`() {
        assertEquals(NoopInternalNetworkApi, HttpUrlConnectionTracker.getInternalNetworkApi())
        assertFalse(EmbraceUrlStreamHandler.enableRequestSizeCapture)
        HttpUrlConnectionTracker.registerUrlStreamHandlerFactory(
            requestContentLengthCaptureEnabled = true,
            sdkStateApi = fakeSdkStateApi,
            instrumentationApi = fakeInstrumentationApi,
            networkRequestApi = fakeNetworkingApi,
            internalInterface = fakeInternalInterface,
        )
        assertNotEquals(NoopInternalNetworkApi, HttpUrlConnectionTracker.getInternalNetworkApi())
        assertTrue(EmbraceUrlStreamHandler.enableRequestSizeCapture)
        HttpUrlConnectionTracker.registerUrlStreamHandlerFactory(
            requestContentLengthCaptureEnabled = false,
            sdkStateApi = fakeSdkStateApi,
            instrumentationApi = fakeInstrumentationApi,
            networkRequestApi = fakeNetworkingApi,
            internalInterface = fakeInternalInterface,
        )
        assertFalse(EmbraceUrlStreamHandler.enableRequestSizeCapture)
    }
}
