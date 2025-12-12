package io.embrace.android.embracesdk.instrumentation.huc

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeEmbraceInternalInterface
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class HttpUrlConnectionTrackerTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var fakeNetworkingApi: NetworkRequestApi
    private lateinit var fakeInternalInterface: EmbraceInternalInterface

    @Before
    fun setup() {
        args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext(), logger = FakeEmbLogger(false))
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
    }
}
