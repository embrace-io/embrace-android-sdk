package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.fakeContext
import io.embrace.android.embracesdk.fakes.unimplemented
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.FlutterSdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
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
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        impl = HostedSdkInternalInterfaceImpl(
            logsApi = unimplemented(),
            configService = FakeConfigService(),
            resourceSource = FakeEnvelopeResourceSource(),
            instrumentationRegistry = unimplemented(),
            serializer = unimplemented(),
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
        impl.setJavaScriptBundleUrl(fakeContext(), "url")
        impl.logHandledUnityException("name", "message", null)
        assertEquals(listOf("set_javascript_bundle_url", "log_handled_unity_exception"), telemetryService.apiCalls)
        assertEquals(
            listOf(SdkErrorType.PublicApiFail.toString(), SdkErrorType.PublicApiFail.toString()),
            errorHandler.sdkErrorMessages.map { it.msg },
        )
    }

    @Test
    fun `network span forwarding defaults to disabled when the sdk throws`() {
        val throwingImpl = HostedSdkInternalInterfaceImpl(
            logsApi = unimplemented(),
            configService = unimplemented(),
            resourceSource = unimplemented(),
            instrumentationRegistry = unimplemented(),
            serializer = unimplemented(),
            rnBundleIdTracker = lazy { unimplemented() },
            hostedSdkVersionInfo = unimplemented(),
            errorHandler = errorHandler,
            telemetryService = telemetryService,
        )
        assertFalse(throwingImpl.isNetworkSpanForwardingEnabled())
        assertEquals(1, errorHandler.sdkErrorMessages.size)
    }
}
