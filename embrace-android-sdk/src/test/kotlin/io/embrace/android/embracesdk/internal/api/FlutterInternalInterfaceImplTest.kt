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
    private lateinit var store: FakePersistedSdkStateStore

    @Before
    fun setUp() {
        store = FakePersistedSdkStateStore()
        hostedSdkVersionInfo = createHostedSdkVersionInfo(AppFramework.FLUTTER, store, FakeErrorHandler())
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
    fun testSetHostedSdkVersions() {
        impl.setHostedSdkVersions("2.12", "3.1")
        assertEquals(1, store.updateCount)
        assertEquals("2.12", hostedSdkVersionInfo.hostedSdkVersion)
        assertEquals("3.1", hostedSdkVersionInfo.hostedPlatformVersion)
    }

    @Test
    fun testSetHostedSdkVersionsNullKeepsValues() {
        impl.setHostedSdkVersions("2.12", "3.1")
        impl.setHostedSdkVersions(null, null)
        assertEquals(1, store.updateCount)
        assertEquals("2.12", hostedSdkVersionInfo.hostedSdkVersion)
        assertEquals("3.1", hostedSdkVersionInfo.hostedPlatformVersion)
    }

    @Test
    fun testSetHostedSdkVersionsPartial() {
        impl.setHostedSdkVersions("2.12", "3.1")
        impl.setHostedSdkVersions("2.13", null)
        assertEquals("2.13", hostedSdkVersionInfo.hostedSdkVersion)
        assertEquals("3.1", hostedSdkVersionInfo.hostedPlatformVersion)
    }
}
