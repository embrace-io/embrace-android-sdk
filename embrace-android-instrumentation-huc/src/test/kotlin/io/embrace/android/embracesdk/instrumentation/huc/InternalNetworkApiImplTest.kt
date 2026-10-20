package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fixtures.fakeCompleteEmbraceNetworkRequest
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkRequestDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.RequestEndData
import io.embrace.android.embracesdk.internal.instrumentation.network.RequestStartData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

internal class InternalNetworkApiImplTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var requestDataSource: FakeNetworkRequestDataSource

    @Before
    fun setup() {
        args = FakeInstrumentationArgs(errorHandler = FakeErrorHandler(false))
        requestDataSource = FakeNetworkRequestDataSource()
    }

    @Test
    fun `verify default state`() {
        with(initialize()) {
            verifyDelegation()
            recordNetworkRequest(fakeCompleteEmbraceNetworkRequest)
            val observed = requestDataSource.requests.single()
            assertEquals(fakeCompleteEmbraceNetworkRequest.url, observed.url)
            assertEquals(fakeCompleteEmbraceNetworkRequest.responseCode, observed.statusCode)
            assertEquals(fakeCompleteEmbraceNetworkRequest.startTime, observed.startTime)
            assertEquals(fakeCompleteEmbraceNetworkRequest.endTime, observed.endTime)
            val exception = RuntimeException()
            logSdkError(exception)
            assertEquals(exception, args.errorHandler.sdkErrorMessages.single().throwable)
        }
    }

    @Test
    fun `check disable everything`() {
        args.clock.setCurrentTime(0)
        initialize().verifyDelegation()
    }

    @Test
    fun `check enable everything`() {
        args.clock.setCurrentTime(1000L)
        args.configService.networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(true)
        initialize().verifyDelegation()
    }

    private fun initialize(): InternalNetworkApiImpl = InternalNetworkApiImpl(
        args = args,
        networkRequestDataSource = requestDataSource,
        networkCaptureDataSource = null,
    )

    private fun InternalNetworkApi.verifyDelegation() {
        assertEquals(args.clock.now(), getSdkCurrentTimeMs())
        assertEquals(args.configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled(), isNetworkSpanForwardingEnabled())
        assertFalse(shouldCaptureNetworkBody("foo", "GET"))
    }

    private class FakeNetworkRequestDataSource : NetworkRequestDataSource {
        override val instrumentationName: String = "fake_network_request_data_source"

        val requests: MutableList<HttpNetworkRequest> = mutableListOf()

        override fun recordNetworkRequest(request: HttpNetworkRequest) {
            requests.add(request)
        }

        override fun startRequest(startData: RequestStartData): String? = null

        override fun endRequest(endData: RequestEndData) {
        }

        override fun discardRequest(id: String) {
        }

        override fun onDataCaptureEnabled() {
        }

        override fun onDataCaptureDisabled() {
        }

        override fun resetDataCaptureLimits() {
        }

        override fun <T> captureTelemetry(
            inputValidation: () -> Boolean,
            invalidInputCallback: () -> Unit,
            action: TelemetryDestination.() -> T?,
        ): T? = null
    }
}
