package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.FlutterSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.mockk.mockk
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
            logsApi = mockk(),
            configService = mockk(),
            resourceSource = mockk(),
            instrumentationRegistry = mockk(),
            serializer = mockk(),
            rnBundleIdTracker = lazy { mockk() },
            hostedSdkVersionInfo = hostedSdkVersionInfo,
            logger = mockk(),
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
