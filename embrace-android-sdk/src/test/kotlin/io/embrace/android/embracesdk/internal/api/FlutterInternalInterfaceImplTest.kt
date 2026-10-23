package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.unimplemented
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.FlutterSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class FlutterInternalInterfaceImplTest {

    private lateinit var impl: HostedSdkInternalInterfaceImpl
    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo

    @Before
    fun setUp() {
        hostedSdkVersionInfo = FlutterSdkVersionInfo(FakeKeyValueStore())
        impl = HostedSdkInternalInterfaceImpl(
            logsApi = unimplemented(),
            configService = unimplemented(),
            resourceSource = unimplemented(),
            instrumentationRegistry = unimplemented(),
            serializer = unimplemented(),
            rnBundleIdTracker = lazy { unimplemented() },
            hostedSdkVersionInfo = hostedSdkVersionInfo,
            errorHandler = FakeErrorHandler(),
            telemetryService = FakeInternalTelemetryService(),
        )
    }

    @Test
    fun testSetFlutterSdkVersion() {
        impl.setEmbraceFlutterSdkVersion("2.12")
        impl.setEmbraceFlutterSdkVersion(null)
        assertEquals("2.12", hostedSdkVersionInfo.hostedSdkVersion)
    }

    @Test
    fun testSetDartVersion() {
        impl.setDartVersion("3.1")
        impl.setDartVersion(null)
        assertEquals("3.1", hostedSdkVersionInfo.hostedPlatformVersion)
    }
}
