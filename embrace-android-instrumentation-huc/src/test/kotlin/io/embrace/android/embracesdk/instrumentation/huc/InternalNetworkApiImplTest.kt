package io.embrace.android.embracesdk.instrumentation.huc

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbraceInternalInterface
import io.embrace.android.embracesdk.fakes.FakeInstrumentationApi
import io.embrace.android.embracesdk.fakes.FakeSdkStateApi
import io.embrace.android.embracesdk.fixtures.fakeCompleteEmbraceNetworkRequest
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class InternalNetworkApiImplTest {
    private lateinit var fakeSdkStateApi: SdkStateApi
    private lateinit var fakeInstrumentationApi: InstrumentationApi
    private lateinit var fakeNetworkingApi: FakeNetworkRequestApi
    private lateinit var fakeInternalInterface: FakeEmbraceInternalInterface

    @Before
    fun setup() {
        fakeSdkStateApi = FakeSdkStateApi()
        fakeInstrumentationApi = FakeInstrumentationApi()
        fakeNetworkingApi = FakeNetworkRequestApi()
        fakeInternalInterface = FakeEmbraceInternalInterface()
    }

    @Test
    fun `verify default state`() {
        with(initialize()) {
            verifyDelegation()
            recordNetworkRequest(fakeCompleteEmbraceNetworkRequest)
            assertEquals(fakeCompleteEmbraceNetworkRequest, fakeNetworkingApi.requests.single())
            val exception = RuntimeException()
            logInternalError(exception)
            assertEquals(exception, fakeInternalInterface.internalErrors.single())
        }
    }

    @Test
    fun `check disable everything`() {
        fakeSdkStateApi = FakeSdkStateApi(
            isStarted = false
        )
        fakeInstrumentationApi = FakeInstrumentationApi(
            sdkTimeMs = 0L
        )
        fakeNetworkingApi = FakeNetworkRequestApi(
            traceparent = null
        )
        fakeInternalInterface = FakeEmbraceInternalInterface(
            networkSpanForwardingEnabled = false
        )
        initialize().verifyDelegation()
    }

    @Test
    fun `check enable everything`() {
        fakeSdkStateApi = FakeSdkStateApi(
            isStarted = true
        )
        fakeInstrumentationApi = FakeInstrumentationApi(
            sdkTimeMs = 1000L
        )
        fakeNetworkingApi = FakeNetworkRequestApi(
            traceparent = "foo"
        )
        fakeInternalInterface = FakeEmbraceInternalInterface(
            networkSpanForwardingEnabled = true
        )
        initialize().verifyDelegation()
    }

    private fun initialize(): InternalNetworkApiImpl = InternalNetworkApiImpl(
        sdkStateApi = fakeSdkStateApi,
        instrumentationApi = fakeInstrumentationApi,
        networkRequestApi = fakeNetworkingApi,
        internalInterface = fakeInternalInterface
    )

    private fun InternalNetworkApi.verifyDelegation() {
        assertEquals(fakeSdkStateApi.isStarted, isStarted())
        assertEquals(fakeInstrumentationApi.getSdkCurrentTimeMs(), getSdkCurrentTimeMs())
        assertEquals(fakeInternalInterface.isNetworkSpanForwardingEnabled(), isNetworkSpanForwardingEnabled())
        assertEquals(fakeInternalInterface.shouldCaptureNetworkBody("foo", "GET"), shouldCaptureNetworkBody("foo", "GET"))
    }
}
