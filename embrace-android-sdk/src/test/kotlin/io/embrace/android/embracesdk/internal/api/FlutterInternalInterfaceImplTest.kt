package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakePersistedSdkStateStore
import io.embrace.android.embracesdk.fakes.unimplemented
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.injection.createHostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class FlutterInternalInterfaceImplTest {

    private lateinit var impl: HostedSdkInternalInterfaceImpl
    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo

    @Before
    fun setUp() {
        hostedSdkVersionInfo = createHostedSdkVersionInfo(AppFramework.FLUTTER, FakePersistedSdkStateStore(), FakeErrorHandler())
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
    fun testSetHostedSdkVersion() {
        impl.setHostedSdkVersion("2.12")
        impl.setHostedSdkVersion(null)
        assertEquals("2.12", hostedSdkVersionInfo.hostedSdkVersion)
    }

    @Test
    fun testSetHostedPlatformVersion() {
        impl.setHostedPlatformVersion("3.1")
        impl.setHostedPlatformVersion(null)
        assertEquals("3.1", hostedSdkVersionInfo.hostedPlatformVersion)
    }
}
