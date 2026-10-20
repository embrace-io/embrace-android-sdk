package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.FlutterSdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

internal class HostedSdkInternalInterfaceImplTest {

    private lateinit var telemetryService: FakeInternalTelemetryService
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var impl: HostedSdkInternalInterfaceImpl

    @Before
    fun setUp() {
        telemetryService = FakeInternalTelemetryService()
        errorHandler = FakeErrorHandler(throwOnInternalError = false)
        impl = HostedSdkInternalInterfaceImpl(
            logsApi = mockk(),
            configService = FakeConfigService(),
            resourceSource = FakeEnvelopeResourceSource(),
            instrumentationRegistry = mockk(),
            serializer = mockk(),
            rnBundleIdTracker = lazy { error("rn bundle id tracker failure") },
            hostedSdkVersionInfo = FlutterSdkVersionInfo(FakeKeyValueStore()),
            errorHandler = errorHandler,
            telemetryService = telemetryService,
        )
    }

    @Test
    fun `internal api usage is recorded`() {
        impl.addEnvelopeResource("key", "value")
        impl.setDartVersion("3.0")
        impl.setEmbraceFlutterSdkVersion("1.0")
        impl.isNetworkSpanForwardingEnabled()
        assertEquals(
            listOf("add_envelope_resource", "set_dart_version", "set_embrace_flutter_sdk_version"),
            telemetryService.apiCalls,
        )
    }

    @Test
    fun `sdk exceptions are reported and a fallback is returned`() {
        impl.setJavaScriptBundleUrl(mockk(), "url")
        impl.logHandledUnityException("name", "message", null)
        assertEquals(listOf("set_javascript_bundle_url", "log_handled_unity_exception"), telemetryService.apiCalls)
        assertEquals(
            listOf(InternalErrorType.PublicApiFail.toString(), InternalErrorType.PublicApiFail.toString()),
            errorHandler.internalErrorMessages.map { it.msg },
        )
    }

    @Test
    fun `network span forwarding defaults to disabled when the sdk throws`() {
        val throwingImpl = HostedSdkInternalInterfaceImpl(
            logsApi = mockk(),
            configService = mockk(),
            resourceSource = mockk(),
            instrumentationRegistry = mockk(),
            serializer = mockk(),
            rnBundleIdTracker = lazy { mockk() },
            hostedSdkVersionInfo = mockk(),
            errorHandler = errorHandler,
            telemetryService = telemetryService,
        )
        assertFalse(throwingImpl.isNetworkSpanForwardingEnabled())
        assertEquals(1, errorHandler.internalErrorMessages.size)
    }
}
