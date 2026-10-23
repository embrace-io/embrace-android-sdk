package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.unimplemented
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.UnitySdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class UnityInternalInterfaceImplTest {

    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo
    private lateinit var impl: HostedSdkInternalInterfaceImpl
    private lateinit var store: FakeKeyValueStore
    private lateinit var errorHandler: ErrorHandler

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        errorHandler = FakeErrorHandler()
        hostedSdkVersionInfo = UnitySdkVersionInfo(store)
        impl = HostedSdkInternalInterfaceImpl(
            logsApi = unimplemented(),
            configService = unimplemented(),
            resourceSource = unimplemented(),
            instrumentationRegistry = unimplemented(),
            serializer = unimplemented(),
            rnBundleIdTracker = lazy { unimplemented() },
            hostedSdkVersionInfo = hostedSdkVersionInfo,
            errorHandler = errorHandler,
            telemetryService = FakeInternalTelemetryService(),
        )
    }

    @Test
    fun testSetUnityMetaDataNull() {
        impl.setUnityMetaData(null, null, "unitySdkVersion")
        assertEquals(emptyMap<String, Any?>(), store.values())
    }

    @Test
    fun testSetUnityMetaDataUsesSingleCommit() {
        impl.setUnityMetaData("unityVersion", "buildGuid", "unitySdkVersion")

        assertEquals(1, store.commitCount)
        assertEquals("unityVersion", hostedSdkVersionInfo.hostedPlatformVersion)
        assertEquals("unitySdkVersion", hostedSdkVersionInfo.hostedSdkVersion)
        assertEquals("buildGuid", hostedSdkVersionInfo.unityBuildIdNumber)
    }
}
